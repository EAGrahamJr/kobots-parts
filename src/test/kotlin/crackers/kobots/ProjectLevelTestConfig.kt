/*
 * Copyright 2022-2024 by E. A. Graham, Jr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package crackers.kobots

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.junitxml.JunitXmlReporter

/**
 * Everything does this
 */
class ProjectLevelTestConfig : AbstractProjectConfig() {
    init {
        System.setProperty("mqtt.broker", "tcp://localhost:1883")
        displayFullTestPath = true
    }

    override fun extensions(): List<Extension> = listOf(
        JunitXmlReporter(
//            includeContainers = false, // don't write out status for all tests
            useTestPathAsName = true, // use the full test path (ie, includes parent test names)
//            outputDir = "../target/junit-xml" // include to set output dir for maven
        )
    )
}
