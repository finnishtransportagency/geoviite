package fi.fta.geoviite.infra

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

@Profile("nodb")
@Component
class NoTransactionTemplate : TransactionTemplate() {
    override fun <T> execute(action: TransactionCallback<T>): T? {
        throw Exception("No transaction template in nodb tests")
    }

    override fun afterPropertiesSet() {}
}
