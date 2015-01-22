// read logback groovy configuration documentation for details.
jmxConfigurator('ole-migration')

statusListener(OnConsoleStatusListener)

context.name = "migrator"

// single appender, to console

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        Pattern = "%d %level %thread %mdc %logger - %m%n"
    }
}

// INFO level and above only

root(INFO, [ "CONSOLE" ])
