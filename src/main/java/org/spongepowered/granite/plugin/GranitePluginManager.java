/*
 * This file is part of Granite, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <http://github.com/SpongePowered>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.granite.plugin;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Optional;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.granite.Granite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GranitePluginManager implements PluginManager {

    private static final String PLUGIN_DESCRIPTOR = Type.getDescriptor(Plugin.class);

    private final Granite granite;
    private final Map<String, PluginContainer> plugins = new HashMap<>();
    private final Map<Object, PluginContainer> pluginInstances = new IdentityHashMap<>();

    @Inject
    public GranitePluginManager(Granite granite) {
        this.granite = requireNonNull(granite, "granite");
        this.plugins.put(granite.getId(), granite);
        this.pluginInstances.put(granite, granite);
    }

    public void loadPlugins() throws IOException {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(this.granite.getPluginsDirectory(), "*.jar")) {
            for (Path jar : dir) {
                String pluginClassName = null;

                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                            continue;
                        }

                        try (InputStream in = zip.getInputStream(entry)) {
                            if ((pluginClassName = findPlugin(in)) != null) {
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    this.granite.getLogger().error("Failed to load plugin JAR: " + jar, e);
                    continue;
                }

                // Load the plugin
                if (pluginClassName != null) {
                    try {
                        Launch.classLoader.addURL(jar.toUri().toURL());
                        Class<?> pluginClass = Class.forName(pluginClassName);
                        GranitePluginContainer container = new GranitePluginContainer(pluginClass);
                        this.plugins.put(container.getId(), container);
                        this.pluginInstances.put(container.getInstance(), container);
                        this.granite.getGame().getEventManager().register(container, container.getInstance());

                        this.granite.getLogger().info("Loaded plugin: {} (from {})", container.getName(), jar);
                    } catch (Throwable e) {
                        this.granite.getLogger().error("Failed to load plugin: " + pluginClassName + " (from " + jar + ')', e);
                    }
                }
            }
        }
    }

    @Nullable
    private String findPlugin(InputStream in) throws IOException {
        ClassReader reader = new ClassReader(in);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode node : classNode.visibleAnnotations) {
                if (node.desc.equals(PLUGIN_DESCRIPTOR)) {
                    return classNode.name.replace('/', '.');
                }
            }
        }

        return null;
    }

    @Override
    public Optional<PluginContainer> fromInstance(Object instance) {
        requireNonNull(instance, "instance");

        if (instance instanceof GranitePluginContainer) {
            return Optional.of((PluginContainer) instance);
        }

        return Optional.fromNullable(this.pluginInstances.get(instance));
    }

    @Override
    public Optional<PluginContainer> getPlugin(String id) {
        return Optional.fromNullable(this.plugins.get(id));
    }

    @Override
    public Logger getLogger(PluginContainer plugin) {
        return ((GranitePluginContainer) plugin).getLogger();
    }

    @Override
    public Collection<PluginContainer> getPlugins() {
        return Collections.unmodifiableCollection(this.plugins.values());
    }

    @Override
    public boolean isLoaded(String id) {
        return this.plugins.containsKey(id);
    }

}
