/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

class StdioIntegrationTest extends AbstractIntegrationSpec {

    def "stdio is made available to build"() {
        given:
        buildFile << """
task echo << {
    def reader = new BufferedReader(new InputStreamReader(System.in))
    def line
    while (line != "close") {
        line = reader.readLine() // readline will chomp the newline off the end
        println line
    }
}
"""

        when:
        executer.withStdIn("abc\n123\nclose\n").withArguments("-s")
        run "echo"

        then:
        output.contains("abc\n123\n")
    }
}