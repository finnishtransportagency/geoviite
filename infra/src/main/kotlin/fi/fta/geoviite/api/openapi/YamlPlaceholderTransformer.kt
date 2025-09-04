package fi.fta.geoviite.api.openapi

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.Resource
import org.springframework.web.servlet.resource.ResourceTransformer
import org.springframework.web.servlet.resource.ResourceTransformerChain
import org.springframework.web.servlet.resource.TransformedResource

class YamlPlaceholderTransformer(private val replacements: Map<String, String>) : ResourceTransformer {
    override fun transform(
        request: HttpServletRequest,
        resource: Resource,
        transformerChain: ResourceTransformerChain,
    ): Resource {
        val transformed = transformerChain.transform(request, resource)

        val filename = transformed.filename
        val isYaml = filename != null && (filename.endsWith(".yml") || filename.endsWith(".yaml"))
        if (isYaml) {
            val content = transformed.inputStream.bufferedReader().readText()
            val replaced = replacements.entries.fold(content) { acc, (key, value) -> acc.replace("{$key}", value) }

            return TransformedResource(transformed, replaced.toByteArray())
        } else {
            return transformed
        }
    }
}
