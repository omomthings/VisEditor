/*******************************************************************************
 * Copyright 2014 Pawel Pastuszak
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
 ******************************************************************************/

package pl.kotcrab.vis.sceneeditor;

class EditorAction {
	public ActionType type;
	public Object obj;
	public float xVal;
	public float yVal;

	public EditorAction (Object obj, ActionType type, float xDiff, float yDiff) {
		this.obj = obj;
		this.type = type;
		this.xVal = xDiff;
		this.yVal = yDiff;
	}
}

enum ActionType {
	POS, SIZE, SCALE, ORIGIN, ROTATION
}
