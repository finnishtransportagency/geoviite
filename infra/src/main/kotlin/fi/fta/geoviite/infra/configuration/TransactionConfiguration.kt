package fi.fta.geoviite.infra.configuration

import java.lang.reflect.AnnotatedElement
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Role
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.annotation.ProxyTransactionManagementConfiguration
import org.springframework.transaction.interceptor.DelegatingTransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttribute
import org.springframework.transaction.interceptor.TransactionAttributeSource

@Configuration
class TransactionConfiguration : ProxyTransactionManagementConfiguration() {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    override fun transactionAttributeSource(): TransactionAttributeSource =
        object : AnnotationTransactionAttributeSource() {
            override fun determineTransactionAttribute(element: AnnotatedElement): TransactionAttribute? {
                val txAttr = super.determineTransactionAttribute(element) ?: return null
                return object : DelegatingTransactionAttribute(txAttr) {
                    /**
                     * By default, transactions are rolled back only on non-checked exceptions. In Kotlin that can lead
                     * to easy mistakes, as there are no checked ones as such, but you can still create them. Also, we
                     * don't want to use exceptions as normal program flow, so not rolling back doesn't make sense.
                     */
                    override fun rollbackOn(ex: Throwable) = true
                }
            }
        }
}
