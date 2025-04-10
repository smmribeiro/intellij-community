// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.directoryStreamIfExists
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

internal class ClassLoaderConfiguratorTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  val rootDir: Path get() = inMemoryFs.fs.getPath("/")

  @Test
  fun `plugin must be after child`() {
    val pluginId = PluginId.getId("org.jetbrains.kotlin")
    val emptyPath = Path.of("")
    val plugins = arrayOf(
      IdeaPluginDescriptorImpl(PluginDescriptorBuilder.builder().build(), emptyPath, isBundled = false, id = pluginId, moduleName = null),
      IdeaPluginDescriptorImpl(PluginDescriptorBuilder.builder().build(), emptyPath, isBundled = false, id = PluginId.getId("org.jetbrains.plugins.gradle"), moduleName = null),
      IdeaPluginDescriptorImpl(PluginDescriptorBuilder.builder().build(), emptyPath, isBundled = false, id = pluginId, moduleName = "kotlin.gradle.gradle-java", moduleLoadingRule = ModuleLoadingRule.OPTIONAL),
      IdeaPluginDescriptorImpl(PluginDescriptorBuilder.builder().build(), emptyPath, isBundled = false, id = pluginId, moduleName = "kotlin.compiler-plugins.annotation-based-compiler-support.gradle", moduleLoadingRule = ModuleLoadingRule.OPTIONAL),
    )
    sortDependenciesInPlace(plugins)
    assertThat(plugins.last().moduleName).isNull()
  }

  @Test
  fun `child with common package prefix must be after included sibling`() {
    val pluginId = PluginId.getId("com.example")
    val emptyPath = Path.of("")

    fun createModuleDescriptor(name: String): IdeaPluginDescriptorImpl {
      return IdeaPluginDescriptorImpl(raw = PluginDescriptorBuilder.builder().apply { `package` = name }.build(),
                                      pluginPath = emptyPath,
                                      isBundled = false,
                                      id = pluginId,
                                      moduleName = name,
                                      moduleLoadingRule = ModuleLoadingRule.OPTIONAL)
    }

    val modules = arrayOf(
      createModuleDescriptor("com.foo"),
      createModuleDescriptor("com.foo.bar"),
    )
    sortDependenciesInPlace(modules)
    assertThat(modules.map { it.moduleName }).containsExactly("com.foo.bar", "com.foo")
  }

  @Test
  fun regularPluginClassLoaderIsUsedIfPackageSpecified() {
    val loadingResult = loadPlugins(modulePackage = "com.example.extraSupportedFeature")
    val plugin = loadingResult
      .enabledPlugins
      .get(1)
    assertThat(plugin.content.modules.get(0).requireDescriptor().pluginClassLoader).isInstanceOf(PluginAwareClassLoader::class.java)

    val scope = createPluginDependencyAndContentBasedScope(plugin, PluginSetBuilder(
      loadingResult.enabledPlugins).createPluginSetWithEnabledModulesMap())!!
    assertThat(scope.isDefinitelyAlienClass(name = "dd", packagePrefix = "dd", force = false)).isNull()
    assertThat(scope.isDefinitelyAlienClass(name = "com.example.extraSupportedFeature.Foo", packagePrefix = "com.example.extraSupportedFeature.", force = false))
      .isEqualToIgnoringWhitespace("Class com.example.extraSupportedFeature.Foo must not be requested from main classloader of p_dependent plugin. " +
                 "Matches content module (packagePrefix=com.example.extraSupportedFeature., moduleName=com.example.sub).")
  }

  @Test
  fun `inject content module if another plugin specifies dependency in old format`() {
    val rootDir = inMemoryFs.fs.getPath("/")

    plugin(rootDir, """
    <idea-plugin package="com.foo">
      <id>1-foo</id>
      <content>
        <module name="com.example.sub"/>
      </content>
    </idea-plugin>
    """)
    module(rootDir, "1-foo", "com.example.sub", """
      <idea-plugin package="com.foo.sub">
      </idea-plugin>
    """)

    plugin(rootDir, """
    <idea-plugin>
      <id>2-bar</id>
      <depends>1-foo</depends>
    </idea-plugin>
    """)

    val plugins = runBlocking { loadDescriptors (rootDir).enabledPlugins }
    assertThat(plugins).hasSize(2)
    val barPlugin = plugins.get(1)
    assertThat(barPlugin.pluginId.idString).isEqualTo("2-bar")

    val classLoaderConfigurator = ClassLoaderConfigurator(PluginSetBuilder(plugins).createPluginSetWithEnabledModulesMap())
    classLoaderConfigurator.configure()

    assertThat((barPlugin.pluginClassLoader as PluginClassLoader)._getParents().map { it.descriptorPath })
      .containsExactly("com.example.sub.xml", null)
  }

  private fun loadPlugins(modulePackage: String?): PluginLoadingResult {
    val dependencyId = "p_dependency"
    PluginBuilder.empty()
      .id(dependencyId)
      .packagePrefix("com.bar")
      .extensionPoints("""<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>""")
      .build(rootDir.resolve(dependencyId))

    val dependentPluginId = "p_dependent"
    PluginBuilder.empty()
      .id(dependentPluginId)
      .packagePrefix("com.example")
      .module("com.example.sub",
              PluginBuilder.empty().packagePrefix(modulePackage)
                .extensionPoints("""<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""))
      .build(rootDir.resolve(dependentPluginId))

    val loadResult = runBlocking { loadDescriptors(rootDir) }
    val plugins = loadResult.enabledPlugins
    assertThat(plugins).hasSize(2)

    val classLoaderConfigurator = ClassLoaderConfigurator(PluginSetBuilder(plugins).createPluginSetWithEnabledModulesMap())
    classLoaderConfigurator.configure()
    return loadResult
  }
}

internal fun loadDescriptors(dir: Path): PluginLoadingResult {
  val buildNumber = BuildNumber.fromString("2042.0")!!
  val result = PluginLoadingResult()
  val context = DescriptorListLoadingContext(customDisabledPlugins = emptySet(),
                                             customBrokenPluginVersions = emptyMap(),
                                             productBuildNumber = { buildNumber })

  // constant order in tests
  val paths = dir.directoryStreamIfExists { it.sorted() }!!
  context.use {
    result.addAll(descriptors = paths.asSequence().mapNotNull { loadDescriptor(file = it, parentContext = context, pool = ZipFilePoolImpl()) },
                  overrideUseIfCompatible = false,
                  productBuildNumber = buildNumber)
  }
  return result
}
