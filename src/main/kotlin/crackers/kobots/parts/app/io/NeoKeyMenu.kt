/*
 * Copyright 2022-2023 by E. A. Graham, Jr.
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

package crackers.kobots.parts.app.io

import crackers.kobots.graphics.loadImage
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Image
import kotlin.math.min

/**
 * An "actionable" menu using the NeoKey. Due to the limited number of buttons, if there are more than 4 items, the menu
 * can be "rotated" to show a subset of the menu items, plus a "next" item. The "next" item will rotate the menu to the
 * next subset of items. A list of 4 or fewer items will always be sent to the [display],
 *
 * The calling application can use the return map to determine which button was pressed and what action to take,
 * executing more than one if desired. This also allows the application to use the keys to "chord" actions that are
 * not necessarily menu items.
 */
open class NeoKeyMenu(val neoKey: NeoKeyHandler, val display: MenuDisplay, items: List<MenuItem>) {
    private val logger by lazy { LoggerFactory.getLogger("NeoKeyMenu") }

    // clone immutable list
    private val menuItems = items.toList()
    private val maxItemsToShow = min(items.size, neoKey.numberOfButtons)
    private var leftMostIndex = 0

    // copy of menu items that can be displayed/acted upon
    private val displayMenu = mutableListOf<MenuItem>()

    /**
     * Displays menu items.
     */
    interface MenuDisplay {
        fun displayItems(items: List<MenuItem>)
    }

    /**
     * Describes a menu item.
     * @param name the name of the menu item
     * @param abbrev an optional abbreviation for the menu item
     * @param icon an optional icon for the menu item
     * @param buttonColor the color to use for the button (default GREEN)
     * @param action the action to take when the button is pressed
     */
    open class MenuItem(
        val name: String,
        val abbrev: String? = null,
        val icon: Image? = null,
        val buttonColor: Color = Color.GRAY,
        val action: () -> Unit
    ) {
        /**
         * Returns the abbreviation if set, otherwise the name.
         */
        override fun toString() = abbrev ?: name
    }

    /**
     * A menu item that rotates an over-sized menu to the "next" page
     */
    private val nextMenuItem by lazy {
        MenuItem("Next", buttonColor = Color.MAGENTA.darker(), icon = loadImage("/arrow_forward.png")) {
            displayMenuFromIndex(leftMostIndex + maxItemsToShow - 1)
        }
    }

    @Synchronized
    private fun displayMenuFromIndex(proposed: Int) {
        val tentative = mutableListOf<MenuItem>()

        // if the menu only contains num buttons items, use the whole thing
        if (menuItems.size <= maxItemsToShow) {
            tentative.addAll(menuItems)
            // if it's short, fill with NO_KEY
            while (tentative.size < maxItemsToShow) tentative.add(NO_KEY)
        } else {
            // otherwise, use the subset of items (take off 1 for "next")
            leftMostIndex = if (proposed >= menuItems.size) 0 else proposed
            val offset = maxItemsToShow - 1
            val maxIndex = min(leftMostIndex + offset, menuItems.size)
            tentative.addAll(menuItems.subList(leftMostIndex, maxIndex))
            // the last item is always "next", so fill in any "blanks: with NO_KEY
            while (tentative.size < offset) tentative.add(NO_KEY)
            tentative += nextMenuItem
        }
        displayMenu.clear()
        displayMenu.addAll(tentative)
        displayMenu()
    }

    /**
     * Reads the keyboard and maps buttons pressed to actions to be performed.
     */
    @Synchronized
    open fun execute(): List<Pair<Int, MenuItem>> {
        if (displayMenu.isEmpty()) return emptyList()

        return neoKey.read()
            .mapIndexed { index, pressed ->
                index to if (pressed) displayMenu[index] else NO_KEY
            }
            .filter { it.second != NO_KEY }
    }

    /**
     * Convenience function that executes the action described for the "first" (list-order) button pressed. There is
     * **no** error handling around action calls.
     * @return `true` if an action was invoked
     */
    @Synchronized
    open fun firstButton(): Boolean = execute().firstOrNull()?.second?.action?.invoke()?.let { true } ?: false

    /**
     * Sets up the sub-selection of menu items and displays them.
     */
    @Synchronized
    fun displayMenu() {
        if (displayMenu.isEmpty()) displayMenuFromIndex(0)

        // display the stuff and set the button colors
        display.displayItems(displayMenu)
        neoKey.buttonColors = displayMenu.map { it.buttonColor }
    }

    companion object {
        val NO_KEY = MenuItem("Ignored", "", buttonColor = Color.BLACK) {}
    }
}
