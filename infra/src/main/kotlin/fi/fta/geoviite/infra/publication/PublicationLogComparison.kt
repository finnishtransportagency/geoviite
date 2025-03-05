package fi.fta.geoviite.infra.publication

fun <T, U> compareChangeValues(
    change: Change<T>,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    compareChange(
        predicate = { change.new != change.old },
        oldValue = change.old,
        newValue = change.new,
        valueTransform = valueTransform,
        propKey = propKey,
        remark = remark,
        enumLocalizationKey = enumLocalizationKey,
    )

fun <U> compareLength(
    oldValue: Double?,
    newValue: Double?,
    threshold: Double,
    valueTransform: (Double) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    compareChange(
        predicate = {
            if (oldValue != null && newValue != null) lengthDifference(oldValue, newValue) > threshold
            else if (oldValue != null || newValue != null) true else false
        },
        oldValue = oldValue,
        newValue = newValue,
        valueTransform = valueTransform,
        propKey = propKey,
        remark = remark,
        enumLocalizationKey = enumLocalizationKey,
    )

fun <T, U> compareChange(
    predicate: () -> Boolean,
    oldValue: T?,
    newValue: T?,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    if (predicate()) {
        PublicationChange(
            propKey,
            value =
                ChangeValue(
                    oldValue = oldValue?.let(valueTransform),
                    newValue = newValue?.let(valueTransform),
                    localizationKey = enumLocalizationKey,
                ),
            remark,
        )
    } else null
