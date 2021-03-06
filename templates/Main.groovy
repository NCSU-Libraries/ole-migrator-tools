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
package ${packageName}

/**
 * Sample "main" class.  By use of this sort of class, gradle can handle your classpath and dependencies, etc.
 */
class Main {
    public static void main(String [] args) {
        def script = new File("driver.groovy")
        assert script.exists(), "driver.groovy not found."
        Binding  scriptBinding = new Binding()

        // set up and add any objects here you'd want "bound" into your script.
        def shell = new GroovyShell(scriptBinding)
        shell.run(script)
    }
}
