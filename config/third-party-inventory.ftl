Lists of ${dependencyMap?size} third-party dependencies.
<#list dependencyMap as dependency>
<#assign model = dependency.getKey()>
<#assign baseVersion = (model.artifact.baseVersion)!model.version>
<#assign artifactName = (model.name)!model.artifactId>
<#if artifactName?starts_with("Unnamed")>
<#assign artifactName = model.artifactId>
</#if>
<#if model.version != baseVersion>
# Maven resolved ${model.groupId}:${model.artifactId}:${model.version} (base version ${baseVersion})
</#if>
<#list dependency.getValue() as license>(${license}) </#list>${artifactName} (${model.groupId}:${model.artifactId}:${baseVersion} - ${(model.url)!"no url defined"})
</#list>
