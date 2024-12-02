package fi.fta.geoviite.infra

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * This utility allows you to access spring beans and properties in a hacky way, past normal CI mechanisms. It should be
 * used only if normal @Autowire isn't usable.
 */
@Component
@Lazy(false) // we use lazy init in tests by default, but this bean can't be lazy-init'd
class SpringContextUtility : ApplicationContextAware {
    companion object {
        var applicationContext: ApplicationContext? = null

        inline fun <reified T> getBean(): T = getContext().getBean(T::class.java)

        inline fun <reified T> getProperty(key: String): T =
            getContext().environment.getProperty(key, T::class.java)
                ?: throw IllegalStateException("No such property: $key")

        fun getContext() = applicationContext ?: throw IllegalStateException("Application context not initialized")
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        SpringContextUtility.applicationContext = applicationContext
    }
}
