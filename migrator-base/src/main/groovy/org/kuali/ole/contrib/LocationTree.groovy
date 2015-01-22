/*

     Copyright (C) 2015 North Carolina State University

     This program is free software: you can redistribute it and/or modify
     it under the terms of the GNU General Public License as published by
     the Free Software Foundation, either version 3 of the License, or
     (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kuali.ole.contrib

import groovy.text.SimpleTemplateEngine
import groovy.xml.MarkupBuilder

/**
 * Representation of locations to be imported into OLE.
 * Provides facilities for generating OLE SQL and XML for importing locations and location levels.
 *
 * <p>
 *     Usage notes: location codes must be unique (even if you have "stacks" in sixteen different libraries).
 *     Locations in OLE are hierarchical (as evidenced by the fact that this class is a *tree*), and each
 *     is location is assigned to a location level.  Due to the way OLE handles circulation rules (at least as of the 1.6
 *     release), the location level with ID #5, intended to denote where the item is
 *     shelved, is especially important, so a note about creating and assigning levels is in order.
 * </p>
 * <p>
 *     Your site may elect to use fewer than five levels (or more, perhaps, but if so you're on your own here =),
 *     so if you do that, your levels will be padded out to five, with "NOTUSED-#" prepended to your list.
 * </p>
 * <p>
 *    Which levels are actually assigned to your locations will depend on the locations actually in use,
 *    as determined by the list passed in to the constructor.  That is, locations without a parent will be assigned
 *     to the first level defined in the constructor, even if there are NOTUSED levels "above" it.
 * </p>
 * <p>
 *     Typical use:
 *     <code>
 *         def tree = new LocationTree(["CAMPUS", "LIBRARY", "SHELVING"])
 *         tree << [ code: "NORTHCAMPUS", name : "North Campus" ]
 *         tree << [ code: "MAIN", parent: "NORTHCAMPUS", name : "Main Library" ]
 *         tree << [ code: "MEDIA", parent: "NORTHCAMPUS", name : "Media Library"]
 *		   tree << [ code: "WESTCAMPUS", name: "West Campus" ]
 *		   tree << [ code: "FLEEBLE", parent: "WESTCAMPUS", name: "Telemachus P. FleebleSnorger IV Library" ]
 *		   tree << [ code: "MAINSTACKS", parent: "MAIN", name: "Stacks" ]
 *		   tree << [ code: "MEDIASTACKS" : parent: "MEDIA", name: "Stacks" ]
 *		   tree << [ code: "FLEEBLERESERVES", parent: "FLEEBLE", name: "Reserves Desk" ]
 *		   println tree.toSQL()
 *	</code>
 * This will create the following five location levels: <code>NOTUSED-1, NOTUSED-2, CAMPUS, LIBRARY, SHELVING</code>, each of
 * which is the 'parent' of the next.  The "CAMPUS" level will have two locations assigned to it, (North and West),  because
 * these locations do not designate a parent.  The "North Campus" location will have two libraries assigned to it, each
 * with one location, while west campus will have one library  with one location in it.
 * </p>
 *
 * @author Adam Constabaris, $LastChangedBy$
 * @version $LastChangedRevision$
 */
class LocationTree {
	
	// add 'depth()' method on Node class; used to help map levels
	static {
		Node.metaClass.depth = {
			int _depth = 0
			def p = delegate.parent()
			while ( p ) {
				_depth++
				p = p.parent()
			}
			_depth
		}
	}
	
	// indicator for whether tree has had IDs and levels calculated.
	private boolean _compressed

	// root node of tree
    def root = new Node(null, "--root--")

	// the sequence of levels.
	def levelSequence = []

	// maps codes to nodes
    def nodeMap = [:]

	// the levels actually in use.
	def activeLevels = []

	/**
	 * Constructs a new LocationTree with the specified location levels.
	 * @param locationLevels the codes for the location levels to be created.  This will be padded to five
	 * levels if necessary.
	 * @see LocationTree
	 */
	public LocationTree( List locationLevels = [ "LIBRARY", "SHELVING" ] ) {
		 // pad levels out to at least 5.
		activeLevels.addAll( locationLevels )
		levelSequence.addAll( locationLevels )
		while ( levelSequence.size() < 5 ) {
			levelSequence.add(0, "NOTUSED-${ 5 - levelSequence.size()}".toString())
		}
	}

	/**
	 * Computes required values on the individual entries required by OLE's database.
	 * and UUIDs (objectIds in Rice).
	 * @param atts
	 * @return
	 */
    def setDefaults(Map atts) {
		if ( ! atts.code ) {
			throw new IllegalArgumentException("Locations must have at least a 'code' attribute")
		}
		if ( !atts.name ) {
			atts.name = atts.code.toLowerCase().capitalize()
		}
		atts.description = atts.description ?: ""
		atts.uuid = atts.uuid ?: UUID.randomUUID()
		atts		
	}

	Node findLocation(String code) {
        return nodeMap."${code}"
    }

	/**
	 * Resolves references and calculates levels for all the nodes in the tree.  See the class documentation for how
	 * this works.
	 */
	void compressTree() {
		if ( _compressed ) { return }
		// now map each code to its id, whether or not it's active
		int idCounter = 1
		root.breadthFirst().each {
			node ->
				if ( !node.is(root) ) {
					def atts = node.attributes()				
					atts.id = idCounter++
					atts.parentId = node.parent()?.attributes()?.id
					atts.levelCode = activeLevels[node.depth() -1]
					atts.levelId = levelSequence.indexOf(atts.levelCode) +1
				}
		}
		_compressed = true
	}

	/**
	 * Adds a new location to the tree to the tree.  Allows adding either a map or a list.
	 * @param o either a map or a list.
	 * <p>
	 *     The keys of the map are: <code>code,parent,name,description</code>.  If the submitted object
	 *     is a list, then it will be interpreted in that order (first element is 'code', second is 'parent').
	 *     These keys correspond to the identifying code of the location, the code of its parent (if any), the
	 *     display name of the location, and finally a short description of the location.
	 * </p>
	 * <p>With the exception of <code>code</code> (or a 1-element list), all attributes are optional.  If "parent"
	 * is not set, then the location will be inserted at the highest active location level, otherwise it will be inserted
	 * at one level below the level identified by the parent code.
	 * </p>
	 * @return this tree.
	 */
    def leftShift(Object o) {
		_compressed = false
        if ( o != null ) {
            Map atts
            def parent            
            if ( o instanceof Map ) {
                atts = (Map)o
            } else if ( o.metaClass.respondsTo("each") ) {
                atts = [code:o[0],parent:o[1], name: o[2], description:o[3] ] 
            } else {
				// add nothing
                return this
            }

			setDefaults(atts)
            
			if ( atts.parent ) {
                parent = findLocation(atts.parent)
                if ( !parent ) {
						def parentAtts = [code:atts.parent]
						setDefaults(parentAtts)                        
                        parent = root.appendNode(atts.parent, parentAtts, [])
						// needs to handle arbitrary depth trees, I guess? 
                        nodeMap[atts.parent] = root.get(atts.parent)[0]
                    }
                }
                if ( parent == null ) {
                    parent = root
                }
                def newNode = parent.appendNode(atts.code, atts, [] )
                nodeMap[atts.code] = newNode
            }
            this 
    }

	/**
	 * Serializes the given node into OLE's location XML format.
	 * @param builder
	 * @param node
	 * @return
	 */
	def serializeNode(MarkupBuilder builder, node) {
		if ( node instanceof Node ) {
			def atts = node.attributes()
			builder.location {
				locationCode(node.name())
				locationLevelCode(atts.levelCode)
				locationName(atts.name)
				parentLocationCode(atts.parent)				
			}
		}
	}

	/**
	 * Generates OLE's ingestable XML format from a tree.  Note that the output does not include
	 * location <em>levels</em> themselves!
	 * @param indent if <code>true</code>, 'pretty print' the generated XML.
	 * @return the XML representing this tree as a string.
	 */
	def toXML(boolean indent=true) {
		compressTree()
		def sw = new StringWriter()
		def w = indent? new IndentPrinter( new PrintWriter(sw) ) : sw
		
		def xml = new MarkupBuilder(w)
		
		xml.'locationGroup'(xmlns: "http://ole.kuali.org/standards/ole-location") {
			root.breadthFirst().each { it != root && serializeNode(xml,it) }			
		}		
		return sw.toString()
	}
	
	/**
	 * Generates SQL for location levels and locations, based on the tree.
	 * @param output an OutputStream or Writer where output will be sent. If <code>null</code> (the default),
	 * output will be collected by an internal StringWriter.
	 * @return the SQL for creating location level and location entries as a String, or the empty string if
	 * <code>output</code> was non-null.  This string can be executed directly via <code>roovy.loader.Sql.execute</code>,
	 * for example.
	 */
	public String toSQL(output = null) {
		def sw = new StringWriter()
		def pw = output == null ? new PrintWriter(sw) : new PrintWriter(output)

		compressTree()
		
		def engine = new SimpleTemplateEngine()
		def lvlTmpl = engine.createTemplate('''INSERT INTO ole_locn_level_t(level_id,level_cd,level_name,ver_nbr, parent_level,obj_id) VALUES(${id}, '${code}', '${name}', 1, ${parentId}, '${uuid}');''')
			
		def locTmpl = engine.createTemplate('''INSERT INTO ole_locn_t( locn_id, locn_cd, locn_name, ver_nbr, level_id, parent_locn_id, obj_id) VALUES ( ${id}, '${code}', '${name}' ,1, ${levelId}, ${parentId}, '${uuid}');''') 
		
		def parentLevel = null
		levelSequence.eachWithIndex { 
			lvl, idx ->
				def binding = [ id: idx+1, code: lvl, name: lvl.toLowerCase().capitalize(), parentId: parentLevel == null ? 'NULL' : parentLevel, uuid: UUID.randomUUID() ]
				pw.println(lvlTmpl.make(binding))
				parentLevel = idx+1					
		}
		pw.println("INSERT INTO ole_locn_level_s (id) VALUES ( ${levelSequence.size()} );")
				
		int maxId = 0
		nodeMap.each { code, loc ->
			def binding = loc.attributes().clone()
			binding.each { k, v  -> 
				if ( v == null ) {
					binding[k] = 'NULL'
				}
			}
			pw.println( locTmpl.make( binding ) )
			if ( loc.attributes().id > maxId ) { maxId = loc.attributes().id }
		}
		
		pw.println("INSERT INTO ole_locn_s (id) VALUES ( ${++maxId} );")
		return sw.toString()
	}
    
	/**                                                               `
	 * Usage sample and simple tests
	 * @param args
	 */
    public static void main (String [] args) {
        def nt = new LocationTree()
        nt << [ code: "foo", parent: "bar" ]
		nt << [ "hill" ]
		nt << [ "stacks", "hill", "D.H. Hill Stacks" ]	
        assert nt.root.get("bar")[0] instanceof Node
        assert nt.root.get("bar")[0].get("foo")[0] instanceof Node
		
		assert nt.root.get("hill")
		assert nt.root.get("hill")[0].get("stacks")
		
		println nt.toXML()
		println nt.toSQL()
    }
}