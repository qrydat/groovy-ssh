package org.hidetake.groovy.ssh.operation

import com.jcraft.jsch.ChannelShell
import groovy.util.logging.Slf4j
import org.hidetake.groovy.ssh.connection.Connection
import org.hidetake.groovy.ssh.core.settings.LoggingMethod
import org.hidetake.groovy.ssh.interaction.InteractionHandler
import org.hidetake.groovy.ssh.interaction.Interactions
import org.hidetake.groovy.ssh.interaction.Stream

/**
 * A shell operation.
 *
 * @author Hidetake Iwata
 */
@Slf4j
class Shell implements Operation {
    private final Connection connection
    private final ChannelShell channel
    private final Interactions interactions

    def Shell(Connection connection1, ShellSettings settings) {
        connection = connection1
        channel = connection.createShellChannel()
        channel.agentForwarding = settings.agentForwarding

        interactions = new Interactions(channel.outputStream, channel.inputStream, settings.encoding)
        if (settings.inputStream) {
            interactions.add {
                standardInput.withStream {
                    log.debug("Sending to standard input on command $connection.remote.name#$channel.id")
                    try {
                        standardInput << settings.inputStream
                    } finally {
                        if (settings.inputStream instanceof Closeable) {
                            settings.inputStream.close()
                        }
                    }
                }
            }
        }
        if (settings.outputStream) {
            interactions.pipe(Stream.StandardOutput, settings.outputStream)
        }
        if (settings.logging == LoggingMethod.slf4j) {
            def log = Shell.log  // workaround for mock injection in test code
            interactions.add {
                when(line: _, from: standardOutput) {
                    log.info("$connection.remote.name#$channel.id|$it")
                }
                when(line: _, from: standardError) {
                    log.error("$connection.remote.name#$channel.id|$it")
                }
            }
        } else if (settings.logging == LoggingMethod.stdout) {
            interactions.add {
                when(line: _, from: standardOutput) {
                    System.out.println("$connection.remote.name#$channel.id|$it")
                }
                when(line: _, from: standardError) {
                    System.err.println("$connection.remote.name#$channel.id|$it")
                }
            }
        }
        if (settings.interaction) {
            interactions.add(settings.interaction)
        }
    }

    @Override
    int startSync() {
        channel.connect()
        try {
            log.info("Started shell $connection.remote.name#$channel.id")
            interactions.start()
            interactions.waitForEndOfStream()
            while (!channel.closed) {
                sleep(100)
            }
            int exitStatus = channel.exitStatus
            if (exitStatus == 0) {
                log.info("Success shell $connection.remote.name#$channel.id")
            } else {
                log.error("Failed shell $connection.remote.name#$channel.id with status $exitStatus")
            }
            exitStatus
        } finally {
            channel.disconnect()
        }
    }

    @Override
    void startAsync(Closure closure) {
        connection.whenClosed(channel) {
            interactions.waitForEndOfStream()
            int exitStatus = channel.exitStatus
            if (exitStatus == 0) {
                log.info("Success shell $connection.remote.name#$channel.id")
            } else {
                log.error("Failed shell $connection.remote.name#$channel.id with status $exitStatus")
            }
            closure.call(exitStatus)
        }
        channel.connect()
        interactions.start()
        log.info("Started shell $connection.remote.name#$channel.id")
    }

    @Override
    void addInteraction(@DelegatesTo(InteractionHandler) Closure closure) {
        interactions.add(closure)
    }
}
