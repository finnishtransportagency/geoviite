package fi.fta.geoviite.infra.util

sealed class Either<out L, out R> {

    abstract fun <T> fold(processLeft: (left: L) -> T, processRight: (right: R) -> T): T

    abstract fun <LR, RR> map(processLeft: (left: L) -> LR, processRight: (right: R) -> RR): Either<LR, RR>

    abstract fun <LR> mapLeft(processLeft: (left: L) -> LR): Either<LR, R>

    abstract fun <RR> mapRight(processRight: (right: R) -> RR): Either<L, RR>
}

data class Left<L>(val value: L) : Either<L, Nothing>() {

    override fun <T> fold(processLeft: (left: L) -> T, processRight: (right: Nothing) -> T): T = processLeft(value)

    override fun <LR, RR> map(processLeft: (left: L) -> LR, processRight: (right: Nothing) -> RR): Either<LR, RR> =
        Left(processLeft(value))

    override fun <LR> mapLeft(processLeft: (left: L) -> LR): Either<LR, Nothing> = Left(processLeft(value))

    override fun <RR> mapRight(processRight: (right: Nothing) -> RR): Either<L, RR> = this
}

data class Right<R>(val value: R) : Either<Nothing, R>() {

    override fun <T> fold(processLeft: (left: Nothing) -> T, processRight: (right: R) -> T): T = processRight(value)

    override fun <LR, RR> map(processLeft: (left: Nothing) -> LR, processRight: (right: R) -> RR): Either<LR, RR> =
        Right(processRight(value))

    override fun <LR> mapLeft(processLeft: (left: Nothing) -> LR): Either<LR, R> = this

    override fun <RR> mapRight(processRight: (right: R) -> RR): Either<Nothing, RR> = Right(processRight(value))
}

/**
 * Process a list of values partitioned into two lists. processLefts and processRights must both return a list of the
 * same length as they're passed in: If they logically map their inputs to outputs element by element in the order they
 * were passed in, then so does this function.
 */
fun <T, LeftValue, RightValue, Result> processPartitioned(
    values: List<T>,
    extractSide: (value: T) -> Either<LeftValue, RightValue>,
    processLefts: (lefts: List<LeftValue>) -> List<Result>,
    processRights: (rights: List<RightValue>) -> List<Result>,
): List<Result> {
    val extractedSidesLeft = mutableListOf<Boolean>()
    val lefts = mutableListOf<LeftValue>()
    val rights = mutableListOf<RightValue>()
    values.forEach { value ->
        extractSide(value)
            .fold(
                {
                    lefts.add(it)
                    extractedSidesLeft.add(true)
                },
                {
                    rights.add(it)
                    extractedSidesLeft.add(false)
                },
            )
    }
    val leftResults = processLefts(lefts)
    val rightResults = processRights(rights)
    assert(lefts.size == leftResults.size) {
        "expected ${lefts.size} results from processLefts, but got ${leftResults.size}"
    }
    assert(rights.size == rightResults.size) {
        "expected ${rights.size} results from processLefts, but got ${rightResults.size}"
    }
    var leftIndex = 0
    var rightIndex = 0
    return extractedSidesLeft.map { sideIsLeft ->
        if (sideIsLeft) leftResults[leftIndex++] else rightResults[rightIndex++]
    }
}

/**
 * Call extractSide to partition the values into Left and Right ones, then return the Lefts as is, while calling process
 * on the Rights. process must return a list of the same length as it was passed in, and if it logically maps its inputs
 * to outputs element by element in the order they were passed in, so does this function.
 */
fun <T, ValidInput, ErrorOrProcessedResult> processValidated(
    values: List<T>,
    extractSide: (value: T) -> Either<ErrorOrProcessedResult, ValidInput>,
    process: (input: List<ValidInput>) -> List<ErrorOrProcessedResult>,
): List<ErrorOrProcessedResult> = processPartitioned(values, extractSide, { it }, process)