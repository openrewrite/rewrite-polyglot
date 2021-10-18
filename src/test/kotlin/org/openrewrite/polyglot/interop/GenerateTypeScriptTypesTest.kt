/*
 *  Copyright 2021 the original author or authors.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  https://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openrewrite.polyglot.interop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.java.Java11Parser
import org.openrewrite.scheduling.DirectScheduler
import java.nio.file.Paths

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class GenerateTypeScriptTypesTest {

    @Test
    fun mustGenerateTypeScriptTypes() {
        val parser = Java11Parser.builder().build()
        val ctx = InMemoryExecutionContext { it.printStackTrace() }
        val srcs = parser.parse(
            listOf(Paths.get("../rewrite/rewrite-java/src/main/java/org/openrewrite/java/tree/J.java")),
            null,
            ctx
        )

        val recipe = GenerateTypeScriptTypes()
        val results = recipe.run(srcs, ctx, DirectScheduler.common(), 3, 1)

        assertThat(results).isNotEmpty
        System.out.println((results.get(0).after as GenerateTypeScriptTypes.TypeScriptDefinitionFile).content)
    }

}