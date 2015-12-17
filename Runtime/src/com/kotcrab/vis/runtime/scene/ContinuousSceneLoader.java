package com.kotcrab.vis.runtime.scene;

import com.artemis.Component;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Json;
import com.kotcrab.vis.runtime.RuntimeConfiguration;
import com.kotcrab.vis.runtime.RuntimeContext;
import com.kotcrab.vis.runtime.assets.AtlasRegionAsset;
import com.kotcrab.vis.runtime.assets.BmpFontAsset;
import com.kotcrab.vis.runtime.assets.MusicAsset;
import com.kotcrab.vis.runtime.assets.ParticleAsset;
import com.kotcrab.vis.runtime.assets.PathAsset;
import com.kotcrab.vis.runtime.assets.ShaderAsset;
import com.kotcrab.vis.runtime.assets.SoundAsset;
import com.kotcrab.vis.runtime.assets.SpriterAsset;
import com.kotcrab.vis.runtime.assets.TextureRegionAsset;
import com.kotcrab.vis.runtime.assets.TtfFontAsset;
import com.kotcrab.vis.runtime.assets.VisAssetDescriptor;
import com.kotcrab.vis.runtime.component.AssetReference;
import com.kotcrab.vis.runtime.component.proto.ProtoShader;
import com.kotcrab.vis.runtime.data.EntityData;
import com.kotcrab.vis.runtime.data.SceneData;
import com.kotcrab.vis.runtime.font.BitmapFontProvider;
import com.kotcrab.vis.runtime.font.FontProvider;
import com.kotcrab.vis.runtime.plugin.EntitySupport;
import com.kotcrab.vis.runtime.scene.IntMapJsonSerializer;
import com.kotcrab.vis.runtime.util.EntityEngine;
import com.kotcrab.vis.runtime.util.ImmutableArray;
import com.kotcrab.vis.runtime.util.SpriterData;
import com.kotcrab.vis.runtime.util.json.LibgdxJsonTagRegistrar;
import com.kotcrab.vis.runtime.util.json.RuntimeJsonTags;

/**ContinuousSceneLoader used to load continuous scenes
 * Created by omaro on 16/11/2015.
 */
public class ContinuousSceneLoader extends AsynchronousAssetLoader<ContinuousScene,ContinuousSceneLoader.ContinuousSceneParameter> {
    public static final String DISTANCE_FIELD_SHADER = "com/kotcrab/vis/runtime/bmp-font-df";

    RuntimeConfiguration configuration;
    Batch batch;
    Array <EntitySupport> supports=new Array<EntitySupport>();

    ContinuousScene scene;
    SceneData data;
    private FontProvider bmpFontProvider;
    private FontProvider ttfFontProvider;
    private  boolean distanceFieldShaderLoaded;


    public void setBatch(Batch batch) {
        this.batch=batch;
    }

    public void registerSupport(AssetManager manager, EntitySupport support) {
        supports.add(support);
        support.setLoaders(manager);
    }

    public ContinuousSceneLoader(Batch batch) {
        this(batch, new InternalFileHandleResolver(), new RuntimeConfiguration());
    }

    public ContinuousSceneLoader(Batch batch,RuntimeConfiguration configuration) {
        this(batch, new InternalFileHandleResolver(),configuration );
    }

    public ContinuousSceneLoader(Batch batch,FileHandleResolver resolver, RuntimeConfiguration configuration) {
        super(resolver);
        setBatch(batch);
        this.configuration=configuration;
        bmpFontProvider=new BitmapFontProvider();
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, ContinuousSceneParameter parameter) {
        RuntimeContext context=new RuntimeContext(configuration,batch,manager,new ImmutableArray<EntitySupport>(supports));
        scene =new ContinuousScene(context,data,parameter);

        // FIXME: 17/12/2015 may need to review this!
    }

    @Override
    public ContinuousScene loadSync(AssetManager manager, String fileName, FileHandle file, ContinuousSceneParameter parameter) {
        ContinuousScene s=scene;
        scene=null;
        return s;
    }


    @Override
    public Array<AssetDescriptor> getDependencies (String fileName, FileHandle file, ContinuousSceneParameter parameter) {
        if (batch == null) throw new IllegalStateException("Batch not set, see #setBatch(Batch)");

        Json json = getJson();
        data = json.fromJson(SceneData.class, file);

        Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();
        loadDependencies(dependencies, data.entities);
        return dependencies;
    }

    private void loadDependencies (Array<AssetDescriptor> dependencies, Array<EntityData> entities) {
        for (EntityData entityData : entities) {
            for (Component component : entityData.components) {
                if (component instanceof AssetReference) {
                    VisAssetDescriptor asset = ((AssetReference) component).asset;

                    //TODO refactor
                    if (asset instanceof TextureRegionAsset) {
                        dependencies.add(new AssetDescriptor<TextureAtlas>("textures.atlas", TextureAtlas.class));

                    } else if (asset instanceof AtlasRegionAsset) {
                        AtlasRegionAsset regionAsset = (AtlasRegionAsset) asset;
                        dependencies.add(new AssetDescriptor<TextureAtlas>(regionAsset.getPath(), TextureAtlas.class));

                    } else if (asset instanceof BmpFontAsset) {
                        checkShader(dependencies);
                        bmpFontProvider.load(dependencies, asset);

                    } else if (asset instanceof TtfFontAsset) {
                        ttfFontProvider.load(dependencies, asset);

                    } else if (asset instanceof ParticleAsset) {
                        PathAsset particleAsset = (ParticleAsset) asset;
                        dependencies.add(new AssetDescriptor<ParticleEffect>(particleAsset.getPath(), ParticleEffect.class));

                    } else if (asset instanceof SoundAsset) {
                        SoundAsset soundAsset = (SoundAsset) asset;
                        dependencies.add(new AssetDescriptor<Sound>(soundAsset.getPath(), Sound.class));

                    } else if (asset instanceof MusicAsset) {
                        MusicAsset musicAsset = (MusicAsset) asset;
                        dependencies.add(new AssetDescriptor<Music>(musicAsset.getPath(), Music.class));

                    } else if (asset instanceof SpriterAsset) {
                        SpriterAsset spriterAsset = (SpriterAsset) asset;
                        dependencies.add(new AssetDescriptor<SpriterData>(spriterAsset.getPath(), SpriterData.class));

                    }
                }

                if (component instanceof ProtoShader) {
                    ProtoShader shaderComponent = (ProtoShader) component;
                    ShaderAsset asset = shaderComponent.asset;
                    if (asset != null) {
                        String path = asset.getFragPath().substring(0, asset.getFragPath().length() - 5);
                        dependencies.add(new AssetDescriptor<ShaderProgram>(path, ShaderProgram.class));
                    }
                }

                for (EntitySupport support : supports)
                    support.resolveDependencies(dependencies, entityData, component);
            }
        }
    }

    public void enableFreeType (AssetManager manager, FontProvider fontProvider) {
        this.ttfFontProvider = fontProvider;
        fontProvider.setLoaders(manager);
    }

    private void checkShader (Array<AssetDescriptor> dependencies) {
        if (!distanceFieldShaderLoaded)
            dependencies.add(new AssetDescriptor<ShaderProgram>(Gdx.files.classpath(DISTANCE_FIELD_SHADER), ShaderProgram.class));

        distanceFieldShaderLoaded = true;
    }

    public static Json getJson () {
        Json json = new Json();

        RuntimeJsonTags.registerTags(new LibgdxJsonTagRegistrar(json));

        json.setSerializer(IntMap.class,new IntMapJsonSerializer());

        return json;
    }

    public void setRuntimeConfig (RuntimeConfiguration configuration) {
        this.configuration = configuration;
    }


    /** Allows to add additional system and managers into {@link EntityEngine} */
    static public class ContinuousSceneParameter extends AssetLoaderParameters<ContinuousScene>{
        public SceneConfig config=new SceneConfig();
        /**
         * If true (the default) scene data will be used to determinate whether physics systems needs
         * to be enabled in {@link SceneConfig}. When this is set to false and you want to use physics you must manually
         * enable it in config.
         */
        public boolean respectScenePhysicsSettings=true;
    }
}
