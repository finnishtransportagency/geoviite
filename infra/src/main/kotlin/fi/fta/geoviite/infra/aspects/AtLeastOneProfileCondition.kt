package fi.fta.geoviite.infra.aspects

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(AtLeastOneProfileCondition::class)
annotation class AtLeastOneProfile(vararg val profiles: String)

class AtLeastOneProfileCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val activeProfiles = context.environment.activeProfiles.toSet()
        val annotationProfiles =
            metadata.getAnnotationAttributes(AtLeastOneProfile::class.java.name)?.get("profiles") as? Array<*>

        return annotationProfiles?.any { profile -> profile in activeProfiles } ?: false
    }
}
