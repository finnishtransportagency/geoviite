package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.util.produceIf

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
): PublicationChange<U>? =
    produceIf(predicate()) {
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
    }

fun <T, U> compareChange(
    change: Change<T?>,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
    nullReplacement: U? = null,
    isSame: (old: T, new: T) -> Boolean = { old, new -> old == new },
): PublicationChange<U>? {
    val changed =
        when {
            change.old == null && change.new == null -> false
            change.old == null || change.new == null -> true
            else -> !isSame(change.old, change.new)
        }
    return produceIf(changed) {
        PublicationChange(
            propKey,
            value =
                ChangeValue(
                    oldValue = change.old?.let(valueTransform) ?: nullReplacement,
                    newValue = change.new?.let(valueTransform) ?: nullReplacement,
                    localizationKey = enumLocalizationKey,
                ),
            remark,
        )
    }
}
