package no.nav.henvendelsemigrator.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotliquery.TransactionalSession
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateQueryAction
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

suspend fun <A> transactional(dataSource: DataSource, operation: (TransactionalSession) -> A): A = withContext(Dispatchers.IO) {
    using(sessionOf(dataSource)) { session ->
        session.transaction(operation)
    }
}

fun <A> NullableResultQueryAction<A>.execute(tx: TransactionalSession): A? = tx.run(this)
fun <A> ListResultQueryAction<A>.execute(tx: TransactionalSession): List<A> = tx.run(this)
fun UpdateQueryAction.execute(tx: TransactionalSession): Int = tx.run(this)