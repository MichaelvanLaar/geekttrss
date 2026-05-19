/*
 * Geekttrss is a RSS feed reader application on the Android Platform.
 *
 * Copyright (C) 2017-2025 by Frederic-Charles Barthelery.
 *
 * This file is part of Geekttrss.
 *
 * Geekttrss is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Geekttrss is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Geekttrss.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.geekorum.build

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.DynamicFeaturePlugin
import com.android.build.gradle.TestPlugin
import com.hierynomus.gradle.license.LicenseBasePlugin
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat
import nl.javadude.gradle.plugins.license.License
import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.fileTree
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import java.util.Locale

internal fun Project.configureSourceLicenseChecker(): Unit {
    apply<LicensePlugin>()

    configure<LicenseExtension> {
        header = file("$rootDir/config/license/header.txt")
        mapping("java", "SLASHSTAR_STYLE")
        mapping("kt", "SLASHSTAR_STYLE")
        excludes(listOf("**/*.webp", "**/*.png"))
    }

    // the LicensePlugin doesn't configure itself properly on DynamicFeaturePlugin
    // Copied the code to configure it
    plugins.withType<DynamicFeaturePlugin> {
        configureAndroid()
    }

    // the LicensePlugin doesn't configure itself properly on Android Test plugin
    // Copied the code to configure it
    plugins.withType<TestPlugin> {
        configureAndroid()
    }

    tasks {
        val checkKotlinFilesLicenseTask = register("checkKotlinFilesLicense", LicenseCheck::class.java) {
            source = fileTree("src").apply {
                include("**/*.kt")
            }
        }

        val formatKotlinFilesLicenseTask = register("formatKotlinFilesLicense", LicenseFormat::class.java) {
            source = fileTree("src").apply {
                include("**/*.kt")
            }
        }

        named<Task>(LicenseBasePlugin.getLICENSE_TASK_BASE_NAME()) {
            dependsOn(checkKotlinFilesLicenseTask)
        }

        named<Task>(LicenseBasePlugin.getFORMAT_TASK_BASE_NAME()) {
            dependsOn(formatKotlinFilesLicenseTask)
        }

    }

    tasks.withType<License>().configureEach {
        notCompatibleWithConfigurationCache("License tasks calls getProject() at execution time")
    }

}


private fun Project.configureAndroid() {
    val android = the<CommonExtension>()
    configureSourceSetRule(android.sourceSets, "Android") { ss ->
        @Suppress("DEPRECATION")
        when (ss) {
            // the dsl.AndroidSourceSet don't expose any getter, so we still need to cast it
            is com.android.build.gradle.api.AndroidSourceSet -> {
                val kotlinFileTrees  = ss.kotlin.directories.foldIndexed(fileTree() as FileTree) { index, acc, n ->
                    if (index == 0) { // skip first as it's an empty tree created for typing
                        fileTree(n)
                    } else {
                        acc + fileTree(n)
                    }
                }
                ss.java.getSourceFiles() + ss.res.getSourceFiles() + fileTree(ss.manifest.srcFile) + kotlinFileTrees
            }
            else -> fileTree()
        }
    }
}

/**
 * Dynamically create a task for each sourceSet, and register with check
 */
@Suppress("DefaultLocale")
private fun Project.configureSourceSetRule(androidSourceSetContainer: NamedDomainObjectContainer<out AndroidSourceSet>,
                                           taskInfix: String, sourceSetSources: (AndroidSourceSet) -> FileTree) {
    // This follows the other check task pattern
    androidSourceSetContainer.configureEach {
        val sourceSetTaskName = "${LicenseBasePlugin.getLICENSE_TASK_BASE_NAME()}${taskInfix}${name.capitalize()}"
        logger.info("Adding $sourceSetTaskName task for sourceSet $name")

        val checkTask = tasks.register(sourceSetTaskName, LicenseCheck::class.java)
        configureForSourceSet(this, checkTask, sourceSetSources)

        // Add independent license task, which will perform format
        val sourceSetFormatTaskName = "${LicenseBasePlugin.getFORMAT_TASK_BASE_NAME()}${taskInfix}${name.capitalize()}"
        val formatTask = tasks.register(sourceSetFormatTaskName, LicenseFormat::class.java)
        configureForSourceSet(this, formatTask, sourceSetSources)
    }
}

private fun configureForSourceSet(sourceSet: AndroidSourceSet, task: TaskProvider<out License>, sourceSetSources: (AndroidSourceSet) -> FileTree) {
    task.configure {
        // Explicitly set description
        description = "Scanning license on ${sourceSet.name} files"

        // Default to all source files from SourceSet
        source = sourceSetSources(sourceSet)
    }
}

private fun String.capitalize() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
