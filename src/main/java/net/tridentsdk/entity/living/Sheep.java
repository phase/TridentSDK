/*
 * Trident - A Multithreaded Server Alternative
 * Copyright 2014 The TridentSDK Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.tridentsdk.entity.living;

import net.tridentsdk.base.SubstanceColor;
import net.tridentsdk.entity.decorate.Ageable;
import net.tridentsdk.entity.decorate.Peaceful;

/**
 * Represents a Sheep
 *
 * @author TridentSDK Team
 */
public interface Sheep extends Ageable, Peaceful {
    /**
     * The color of this sheep's wool
     *
     * @return the color of this sheep's wool
     */
    SubstanceColor getColor();

    /**
     * Whether or not this sheep can be sheared
     *
     * @return whether or not this sheep can be sheared
     */
    boolean isShearable();
}