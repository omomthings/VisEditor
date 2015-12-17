/*
 * Copyright 2014-2015 See AUTHORS file.
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

package com.kotcrab.vis.runtime.scene;

import com.artemis.BaseSystem;
import com.artemis.InvocationStrategy;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.runtime.RuntimeConfiguration;
import com.kotcrab.vis.runtime.RuntimeContext;
import com.kotcrab.vis.runtime.data.LayerData;
import com.kotcrab.vis.runtime.data.SceneData;
import com.kotcrab.vis.runtime.plugin.EntitySupport;
import com.kotcrab.vis.runtime.system.CameraManager;
import com.kotcrab.vis.runtime.util.AfterSceneInit;
import com.kotcrab.vis.runtime.util.BootstrapInvocationStrategy;
import com.kotcrab.vis.runtime.util.EntityEngine;
import com.kotcrab.vis.runtime.util.EntityEngineConfiguration;

/**
 * Base class of VisRuntime scene system. Scene are typically constructed using {@link ContinuousVisAssetManager} with {@link ContinuousSceneLoader}
 * @author Kotcrab
 * @contributor omaro
 */
public class ContinuousScene {
    private CameraManager cameraManager;
    private EntityEngine engine;
    private EntityEngineConfiguration engineConfig;

    private Array<LayerData> layerData;


    /** Used by framework, not indented for external use */
    public ContinuousScene (RuntimeContext context, SceneData data, ContinuousSceneLoader.ContinuousSceneParameter parameter) {
        layerData = data.layers;

        AssetManager assetsManager = context.assetsManager;
        RuntimeConfiguration runtimeConfig = context.configuration;

        EntityEngineConfiguration engineConfig = new EntityEngineConfiguration();

        if (parameter == null) parameter = new ContinuousSceneLoader.ContinuousSceneParameter();
        SceneConfig config = parameter.config;
        config.sort();
        // FIXME: 17/12/2015 need to review this! may need to remake the SceneConfig file..

        if (parameter.respectScenePhysicsSettings) {
            if (data.physicsSettings.physicsEnabled) {
                config.enable(SceneFeatureGroup.PHYSICS);
            } else {
                config.disable(SceneFeatureGroup.PHYSICS);
            }
        }


        for (SceneConfig.ConfigElement element : config.getConfigElements()) {
            if (element.disabled) continue;
            engineConfig.setSystem(element.provider.create(engineConfig, context, data));
        }


        cameraManager = engineConfig.getSystem(CameraManager.class);

        for (EntitySupport support : context.supports) {
            support.registerSystems(runtimeConfig, engineConfig, assetsManager);
        }

        this.engineConfig=engineConfig;

    }


    /**Create a unique {@link EntityEngine} for this scene, used to create an engine in a differed time*/
    public void createEntityEngine(){
        if (engine==null)
            engine=new EntityEngine(engineConfig);
    }

    /** Called by framework right after loading scene to finish loading scene and inflate all entities */
    public void init () {
        engine.setInvocationStrategy(new BootstrapInvocationStrategy());
        engine.process();
        engine.setInvocationStrategy(new InvocationStrategy());

        for (BaseSystem system : engine.getSystems()) {
            if (system instanceof AfterSceneInit) {
                ((AfterSceneInit) system).afterSceneInit();
            }
        }
    }

    /**used to call AfterSceneInit of systems after loading a new scene*/
    public void reInit() {
        engine.process();

        for (BaseSystem system : engine.getSystems())
            if (system instanceof AfterSceneInit)
                ((AfterSceneInit) system).afterSceneInit();
    }

    /** Updates and renders entire scene. Typically called from {@link ApplicationListener#render()} */
    public void render () {
        engine.setDelta(Math.min(Gdx.graphics.getDeltaTime(), 1 / 60f));
        engine.process();
    }

    /** Must by called when screen was resized. Typically called from {@link ApplicationListener#resize(int, int)} */
    public void resize (int width, int height) {
        cameraManager.resize(width, height);
    }

    public Array<LayerData> getLayerData () {
        return layerData;
    }

    public LayerData getLayerDataByName (String name) {
        for (LayerData data : layerData) {
            if (data.name.equals(name)) return data;
        }

        return null;
    }

    public EntityEngine getEntityEngine () {
        return engine;
    }

}
