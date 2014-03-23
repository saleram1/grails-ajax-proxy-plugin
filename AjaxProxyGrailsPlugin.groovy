class AjaxProxyGrailsPlugin {
    def version = "0.1.2"
    def grailsVersion = "1.3 > *"

    def author = "Sean Gilligan"
    def authorEmail = "sean at msgilligan dot com"
    def title = "Ajax Proxy Plugin"
    def description = 'Ajax Proxy Plugin (for cross-domain requests)'
    def documentation = "http://grails.org/plugin/ajax-proxy"

    def doWithWebDescriptor = { xml ->

        def config = application.config.plugins.proxy

        String proxyScheme = config.proxyScheme ?: 'http://'
        String proxyHost = config.proxyHost
        String proxyPort = config.proxyPort
        String proxyPath = config.proxyPath
        String proxyPattern = config.proxyBase ?: '/training-module/*'
        String defaultLogin = config.login ?: '/auth/login?targetUri='

        println "Proxy path ${proxyPattern} to URL ${proxyScheme}${proxyHost}:${proxyPort}${proxyPath}"
        println ""

        def servlets = xml.'servlet'
        servlets[servlets.size() - 1] + {
            servlet {
                'servlet-name'('ProxyServlet')
                'servlet-class'('net.edwardstx.ProxyServlet')
                'init-param' {
                    'param-name'('proxyScheme')
                    'param-value'(proxyScheme)
                }
                'init-param' {
                    'param-name'('proxyHost')
                    'param-value'(proxyHost)
                }
                'init-param' {
                    'param-name'('proxyPort')
                    'param-value'(proxyPort)
                }
                'init-param' {
                    'param-name'('proxyPath')
                    'param-value'(proxyPath)
                }
                'init-param' {
                    'param-name'('maxFileUploadSize')
                    'param-value'('1048576')
                }
                'init-param' {
                    'param-name'('login')
                    'param-value'(defaultLogin)
                }
            }
        }

        def servletMappings = xml.'servlet-mapping'
        servletMappings[servletMappings.size() - 1] + {
            'servlet-mapping' {
                'servlet-name'('ProxyServlet')
                'url-pattern'(proxyPattern)
            }
        }
    }
}
