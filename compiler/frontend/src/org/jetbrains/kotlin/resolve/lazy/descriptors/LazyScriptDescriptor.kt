/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.lazy.descriptors

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorVisitor
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.data.KtScriptInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.source.toSourceElement
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionProvider
import org.jetbrains.kotlin.script.ScriptPriorities
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor

class LazyScriptDescriptor(
        resolveSession: ResolveSession,
        containingDeclaration: DeclarationDescriptor,
        name: Name,
        internal val scriptInfo: KtScriptInfo
) : ScriptDescriptor, LazyClassDescriptor(
        resolveSession,
        containingDeclaration,
        name,
        scriptInfo
) {
    init {
        resolveSession.trace.record(BindingContext.SCRIPT, scriptInfo.script, this)
    }

    private val sourceElement = scriptInfo.script.toSourceElement()

    override fun getSource() = sourceElement

    private val priority: Int = ScriptPriorities.getScriptPriority(scriptInfo.script)

    override fun getPriority() = priority

    private val scriptDefinition: KotlinScriptDefinition
            by lazy {
                val file = scriptInfo.script.getContainingKtFile()
                KotlinScriptDefinitionProvider.getInstance(file.project).findScriptDefinition(file.originalFile.virtualFile) ?:
                    throw RuntimeException("file ${file.name} is not a script")
            }

    override fun substitute(substitutor: TypeSubstitutor) = this

    override fun <R, D> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D): R =
            visitor.visitScriptDescriptor(this, data)

    override fun createMemberScope( c: LazyClassContext, declarationProvider: ClassMemberDeclarationProvider): LazyScriptClassMemberScope =
        LazyScriptClassMemberScope(
                // Must be a ResolveSession for scripts
                c as ResolveSession,
                declarationProvider,
                this,
                c.trace
        )

    override fun getUnsubstitutedPrimaryConstructor() = super.getUnsubstitutedPrimaryConstructor()!!

    override fun getInjectedSupertypes(): List<KotlinType>? =
            scriptDefinition.getScriptSupertypes(this).let { if (it.isEmpty()) null else it }

    override fun getScriptParametersToPassToSuperclass(): List<Pair<Name, KotlinType>> {
        val scriptParams = scriptDefinition.getScriptParameters(this)
        return scriptDefinition.getScriptParametersToPassToSuperclass(this).map { name ->
            Pair(name,
                 scriptParams.find { it.name == name }?.type
                    ?: throw RuntimeException("Unknown script parameter '$name' is specified as a script base class parameter"))
        }
    }
}
