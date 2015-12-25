package com.kotcrab.vis.runtime.scene;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kotcrab.vis.runtime.component.PhysicsBody;
import com.kotcrab.vis.runtime.component.Transform;
import com.kotcrab.vis.runtime.data.EntityData;
import com.kotcrab.vis.runtime.data.SceneData;
import com.kotcrab.vis.runtime.font.FontProvider;
import com.kotcrab.vis.runtime.font.FreeTypeFontProvider;
import com.kotcrab.vis.runtime.system.CameraManager;
import com.kotcrab.vis.runtime.util.EntityEngine;

/**
 * Advanced kotcrab.vis.runtime.scene.VisAssetManager used to create and manage levels with continuous scenes without interruption
 * <p/>
 * Created by omaro on 15/11/2015.
 */
public class ContinuousVisAssetManager extends AssetManager {

    enum Direction {LEFT, RIGHT, UP, DOWN}

    ContinuousSceneLoader sceneLoader;
    private Direction secondSceneDirection, firstSceneDirection;
    private int sceneNumber = 0;
    private Array<Entity> firstSceneEntities, secondSceneEntities;
    private Array<Entity> savedEntities = new Array<Entity>();
    private String standardName;
    private int numFormat;
    ContinuousScene scene;

    /**
     * @param standardName Standard Name for all scene files, e.g for files scene/level00.scene - scene/level01.scene etc. pass "scene/level"
     * @param numFormat    Number of numerical characters in the file names e.g for level001.scene , pass 3
     * @param batch        -
     */
    public ContinuousVisAssetManager(String standardName, int numFormat, SpriteBatch batch) {
        super(new InternalFileHandleResolver());
        // TODO: 17/12/2015 consider using different types of file handle resolver
        this.standardName = standardName;
        this.numFormat = numFormat;
        firstSceneEntities = new Array<Entity>();
        secondSceneEntities = new Array<Entity>();
        sceneLoader = new ContinuousSceneLoader(batch);
        setLoader(ContinuousScene.class, sceneLoader);
    }

    public ContinuousScene getSceneInstance() {
        return scene;
    }

    public int getSceneNumber() {
        return sceneNumber;
    }

    /**
     * Initiates the loading of the first and second scene
     */
    public ContinuousScene loadInitialSceneNow() {
        return loadInitialSceneNow(null);
    }

    /**
     * Initiates the loading of the first and second scene with the parameters
     */
    public ContinuousScene loadInitialSceneNow(ContinuousSceneLoader.ContinuousSceneParameter parameter) {
        try {
            //creating the two scenes
            load(getSceneName(sceneNumber), ContinuousScene.class, parameter);
            finishLoading();
            scene = get(getSceneName(sceneNumber), ContinuousScene.class);
            scene.createEntityEngine();
            load(getSceneName(sceneNumber + 1), ContinuousScene.class, parameter);
            finishLoading();

            //then adding scene data and saving it to be removed later
            Json json = SceneLoader.getJson();
            SceneData data = json.fromJson(SceneData.class, Gdx.files.internal(getSceneName(sceneNumber)));
            firstSceneDirection = getDirection(data);
            addDataToScene(data, true);
            data = json.fromJson(SceneData.class, Gdx.files.internal(getSceneName(sceneNumber + 1)));
            secondSceneDirection = getDirection(data);
            addDataToScene(data, false);
            scene.init();
            moveSceneTo(true, secondSceneDirection);
        } catch (GdxRuntimeException e) {
            e.printStackTrace();
            Gdx.app.exit();
        }
        return scene;
    }


    private void addDataToScene(SceneData data, boolean toFirstScene) {
        EntityEngine engine = scene.getEntityEngine();
        for (EntityData entity : data.entities) {
            Entity e = entity.build(engine);
            if (toFirstScene)
                firstSceneEntities.add(e);
            else
                secondSceneEntities.add(e);
        }
    }

    /**
     * returns the name of the given scene number
     */
    String getSceneName(int number) {
        return String.format(standardName + "%0" + numFormat + "d.scene", number);
    }


    public void loadNextScene() {
        Gdx.app.debug("ContinuousVisAssetManager", "Loading next scene");
        sceneNumber++;
        if (Gdx.files.internal(getSceneName(sceneNumber + 1)).exists()) {

            doSaveEntities();
            secondSceneEntities.addAll(savedEntities);


            //Delete the first scene
            for (Entity entity : firstSceneEntities)
                entity.deleteFromWorld();
            firstSceneEntities.clear();

            firstSceneEntities.addAll(secondSceneEntities);
            secondSceneEntities.clear();

            //load dependencies in memory if not already the case
            load(getSceneName(sceneNumber + 1), ContinuousScene.class);
            finishLoading();

            //moves the scene to always stay around the (0,0)
            Json json = SceneLoader.getJson();
            SceneData nextData = json.fromJson(SceneData.class, Gdx.files.internal(getSceneName(sceneNumber + 1)));
            firstSceneDirection = secondSceneDirection;
            secondSceneDirection = getDirection(nextData);
            moveSceneTo(true, secondSceneDirection);
            addDataToScene(nextData, false);
            scene.reInit();
            Gdx.app.debug("ContinuousVisAssetManager", "Finished loading next scene successfully");
        } else {
            Gdx.app.error("ContinuousVisAssetManager", "The scene " + getSceneName(sceneNumber + 1) + " does not exist!");
            sceneNumber--;
        }
    }

    public void loadPreviousScene() {
        Gdx.app.debug("ContinuousVisAssetManager", "Loading previous scene");
        sceneNumber--;
        if (Gdx.files.internal(getSceneName(sceneNumber)).exists()) {

            doSaveEntities();
            firstSceneEntities.addAll(savedEntities);


            for (Entity entity : secondSceneEntities)
                entity.deleteFromWorld();
            secondSceneEntities.clear();
            secondSceneEntities.addAll(firstSceneEntities);
            firstSceneEntities.clear();

            //load dependencies in memory if need so
            load(getSceneName(sceneNumber), ContinuousScene.class);
            finishLoading();

            Json json = SceneLoader.getJson();
            SceneData data = json.fromJson(SceneData.class, Gdx.files.internal(getSceneName(sceneNumber)));

            //Reset previous scene around 0
            moveSceneTo(false, oppositeDirectionOf(secondSceneDirection));
            secondSceneDirection = firstSceneDirection;
            firstSceneDirection = getDirection(data);

            addDataToScene(data, true);
            scene.reInit();
            moveSceneTo(true, secondSceneDirection);

            Gdx.app.debug("ContinuousVisAssetManager", "Finished loading previous scene successfully");
        } else {
            Gdx.app.error("ContinuousVisAssetManager", "The Scene " + getSceneName(sceneNumber) + " does not exist!");
            sceneNumber++;
        }

    }


    /**
     * used to tell the manager entities that should not be remove when switching scenes
     */
    public void doNotDelete(Entity entity) {
        savedEntities.add(entity);
    }

    /**
     * used to replace in the scene entity list an entity that was previously saved
     */
    public void removeSavedEntity(Entity entity) {
        if (savedEntities.removeValue(entity, false)) {
            firstSceneEntities.add(entity);
        }
    }

    private void doSaveEntities() {
        for (Entity entity : savedEntities)
            if (firstSceneEntities.contains(entity, false)) {
                firstSceneEntities.removeValue(entity, false);
            } else if (secondSceneEntities.contains(entity, false)) {
                secondSceneEntities.removeValue(entity, false);
            }
    }

    /**
     * Look through the whole scene searching for the Direction set in one entity's variable component
     *
     * @param data the {@link SceneData} from where to get the {@link Direction}
     * @return the {@link Direction} set in the given data
     */
    private Direction getDirection(SceneData data) {
        Direction direction = Direction.RIGHT;
        String dir;
        dir = data.variables.get("DIRECTION", "none");

        if (dir.equals("UP"))
            direction=Direction.UP;
        else if(dir.equals("DOWN"))
            direction=Direction.DOWN;
        else if(dir.equals("LEFT"))
            direction=Direction.LEFT;
        else if (dir.equals("RIGHT"))
            direction=Direction.RIGHT;
        else if(dir.equals("none"))
            Gdx.app.error("ContinuousVisAssetManager", "WARNING: Direction not found in the scene, Direction is set to default value :RIGHT, this might be wrong for your case," +
                    " please consider putting a variable component in any entity with the key \"DIRECTION\" and the corresponding value in Upper case ");
        else
            Gdx.app.error("ContinuousVisAssetManager", "Direction is incorrectly set in the file " + getSceneName(sceneNumber + 1));

        Gdx.app.debug("ContinuousVisAssetManager", "Direction is set to " + direction);
        return direction;
    }

    /**
     * @param direction the {@link Direction}
     * @return the opposite of the given {@link Direction}
     */

    private Direction oppositeDirectionOf(Direction direction) {
        switch (direction) {
            case UP:
                return Direction.DOWN;
            case DOWN:
                return Direction.UP;
            case LEFT:
                return Direction.RIGHT;
            case RIGHT:
                return Direction.LEFT;
            default:
                return direction;
        }
    }


    /**
     * Moves the scene to always stay on the center, used for internal call
     *
     * @param moveFirstScene true if you want to move the firstScene, false to move the second one
     * @param direction      direction where the next scene should go
     */
    private void moveSceneTo(boolean moveFirstScene, Direction direction) {
        Array<Entity> sceneToMove = (moveFirstScene) ? firstSceneEntities : secondSceneEntities;
        EntityEngine engine = scene.getEntityEngine();
        Viewport viewport = engine.getSystem(CameraManager.class).getViewport();
        // TODO: 03/12/2015 change this in order to have different scene sizes!
        //direction are reversed! e.g if next scene should come on the right, the actual scene goes to left
        float deltaX = 0, deltaY = 0;
        switch (direction) {
            case LEFT:
                deltaX = viewport.getWorldWidth();
                break;
            case RIGHT:
                deltaX = -viewport.getWorldWidth();
                break;
            case UP:
                deltaY = -viewport.getWorldHeight();
                break;
            case DOWN:
                deltaY = viewport.getWorldHeight();
                break;
            default:
                break;
        }

        for (Entity entity : sceneToMove) {
            Transform transform = entity.getComponent(Transform.class);
            if (transform != null)
                transform.setPosition(transform.getX() + deltaX, transform.getY() + deltaY);
            PhysicsBody physicsBody = entity.getComponent(PhysicsBody.class);
            if (physicsBody != null) {
                Vector2 vector2 = physicsBody.body.getPosition().add(deltaX, deltaY);
                physicsBody.body.setTransform(vector2, 0);
            }
        }
    }


    /**
     * Allows to enable FreeType support.
     *
     * @param freeTypeFontProvider must be instance of {@link FreeTypeFontProvider}. Note that this parameter is not checked!
     */
    public void enableFreeType(FontProvider freeTypeFontProvider) {
        if (freeTypeFontProvider != null) sceneLoader.enableFreeType(this, freeTypeFontProvider);
    }

    public ContinuousSceneLoader getSceneLoader() {
        return sceneLoader;
    }
}
