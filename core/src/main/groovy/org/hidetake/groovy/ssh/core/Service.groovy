package org.hidetake.groovy.ssh.core

import groovy.util.logging.Slf4j
import org.hidetake.groovy.ssh.core.container.ContainerBuilder
import org.hidetake.groovy.ssh.core.container.ProxyContainer
import org.hidetake.groovy.ssh.core.container.RemoteContainer
import org.hidetake.groovy.ssh.core.settings.GlobalSettings
import org.hidetake.groovy.ssh.session.Executor

import static org.hidetake.groovy.ssh.util.Utility.callWithDelegate

/**
 * An entry point of SSH service.
 *
 * @author Hidetake Iwata
 */
@Slf4j
class Service {
    /**
     * Container of remote hosts.
     */
    final remotes = new RemoteContainer()

    /**
     * Container of proxy hosts.
     */
    final proxies = new ProxyContainer()

    /**
     * Global settings.
     */
    final settings = new GlobalSettings()

    /**
     * Configure the container of remote hosts.
     *
     * @param closure
     */
    void remotes(Closure closure) {
        assert closure, 'closure must be given'
        def builder = new ContainerBuilder(remotes)
        callWithDelegate(closure, builder)
    }

    /**
     * Configure the container of proxy hosts.
     *
     * @param closure
     */
    void proxies(Closure closure) {
        assert closure, 'closure must be given'
        def builder = new ContainerBuilder(proxies)
        callWithDelegate(closure, builder)
    }

    /**
     * Configure global settings.
     *
     * @param closure
     */
    void settings(@DelegatesTo(GlobalSettings) Closure closure) {
        assert closure, 'closure must be given'
        callWithDelegate(closure, settings)
    }

    /**
     * Run a closure.
     *
     * @param closure
     * @return returned value of the last session
     */
    def run(@DelegatesTo(RunHandler) Closure closure) {
        assert closure, 'closure must be given'
        def handler = new RunHandler()
        callWithDelegate(closure, handler)

        def executor = new Executor(settings, handler.settings)
        def results = executor.execute(handler.sessions)
        results.empty ? null : results.last()
    }
}
