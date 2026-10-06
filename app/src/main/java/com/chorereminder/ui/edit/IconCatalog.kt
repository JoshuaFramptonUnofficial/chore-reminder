package com.chorereminder.ui.edit

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.chorereminder.R

/**
 * Curated, chore-relevant icon set for the picker (FR-4).
 *
 * `material-icons-core` only ships ~50 generic icons (no mop, no laundry, no
 * studying) and `material-icons-extended` alone blew the APK out to 66 MB, so
 * instead these are hand-picked Material Symbols vendored as vector drawables
 * under `res/drawable/ic_chore_*.xml` (~100 icons, ~150 KB total).
 *
 * [key] is what gets persisted (DB column + JSON backups), so these strings are
 * part of the backup format -- renaming one breaks old exports. [keywords]
 * back the search field.
 */
data class ChoreIcon(
    val key: String,
    val label: String,
    @DrawableRes val res: Int,
    val keywords: String = "",
)

data class IconGroup(
    val title: String,
    val icons: List<ChoreIcon>,
)

val ICON_GROUPS: List<IconGroup> = listOf(
    IconGroup(
        "Cleaning",
        listOf(
            ChoreIcon("Mop", "Mop", R.drawable.ic_chore_mop, "floor sweep"),
            ChoreIcon("CleaningServices", "Clean", R.drawable.ic_chore_cleaning_services, "tidy chores"),
            ChoreIcon("Vacuum", "Vacuum", R.drawable.ic_chore_vacuum, "hoover carpet"),
            ChoreIcon("Soap", "Soap", R.drawable.ic_chore_soap, "wash scrub"),
            ChoreIcon("Brush", "Scrub", R.drawable.ic_chore_brush, "brush scrub clean"),
            ChoreIcon("Recycling", "Recycling", R.drawable.ic_chore_recycling, "sort bins"),
            ChoreIcon("Delete", "Trash", R.drawable.ic_chore_delete, "bin rubbish garbage waste"),
            ChoreIcon("Compost", "Compost", R.drawable.ic_chore_compost, "food waste"),
        ),
    ),
    IconGroup(
        "Kitchen",
        listOf(
            ChoreIcon("Kitchen", "Kitchen", R.drawable.ic_chore_kitchen, "sink dishes"),
            ChoreIcon("Countertops", "Counters", R.drawable.ic_chore_countertops, "wipe surface"),
            ChoreIcon("Microwave", "Microwave", R.drawable.ic_chore_microwave, "appliance"),
            ChoreIcon("Blender", "Blender", R.drawable.ic_chore_blender, "appliance"),
            ChoreIcon("CoffeeMaker", "Coffee", R.drawable.ic_chore_coffee_maker, "machine"),
            ChoreIcon("LocalDining", "Dishes", R.drawable.ic_chore_local_dining, "cutlery eat"),
            ChoreIcon("Restaurant", "Cooking", R.drawable.ic_chore_restaurant, "meal food"),
        ),
    ),
    IconGroup(
        "Laundry",
        listOf(
            ChoreIcon("LocalLaundryService", "Laundry", R.drawable.ic_chore_local_laundry_service, "wash machine"),
            ChoreIcon("DryCleaning", "Dry clean", R.drawable.ic_chore_dry_cleaning, "hanger clothes"),
            ChoreIcon("Iron", "Iron", R.drawable.ic_chore_iron, "press clothes"),
            ChoreIcon("Checkroom", "Wardrobe", R.drawable.ic_chore_checkroom, "closet clothes fold"),
        ),
    ),
    IconGroup(
        "Bathroom",
        listOf(
            ChoreIcon("Shower", "Shower", R.drawable.ic_chore_shower, "bathe"),
            ChoreIcon("Bathtub", "Bathtub", R.drawable.ic_chore_bathtub, "bath"),
            ChoreIcon("Wc", "Toilet", R.drawable.ic_chore_wc, "bathroom"),
            ChoreIcon("WaterDrop", "Water", R.drawable.ic_chore_water_drop, "leak plumbing"),
        ),
    ),
    IconGroup(
        "Home & Repair",
        listOf(
            ChoreIcon("Build", "Tools", R.drawable.ic_chore_build, "repair maintenance fix filter"),
            ChoreIcon("Handyman", "Handyman", R.drawable.ic_chore_handyman, "repair fix wrench"),
            ChoreIcon("Hardware", "Hardware", R.drawable.ic_chore_hardware, "tools"),
            ChoreIcon("ElectricalServices", "Electrical", R.drawable.ic_chore_electrical_services, "wiring"),
            ChoreIcon("Lightbulb", "Light bulb", R.drawable.ic_chore_lightbulb, "lamp replace"),
            ChoreIcon("Power", "Power", R.drawable.ic_chore_power, "outlet plug"),
            ChoreIcon("BatteryChargingFull", "Battery", R.drawable.ic_chore_battery_charging_full, "charge replace"),
            ChoreIcon("Window", "Window", R.drawable.ic_chore_window, "glass pane"),
            ChoreIcon("DoorFront", "Door", R.drawable.ic_chore_door_front, "entrance"),
            ChoreIcon("Plumbing", "Plumbing", R.drawable.ic_chore_plumbing, "pipes leak"),
            ChoreIcon("HomeRepairService", "Repair kit", R.drawable.ic_chore_home_repair_service, "toolbox"),
            ChoreIcon("Construction", "Construction", R.drawable.ic_chore_construction, "project build"),
            ChoreIcon("Carpenter", "Carpentry", R.drawable.ic_chore_carpenter, "wood build"),
            ChoreIcon("Cable", "Cables", R.drawable.ic_chore_cable, "wires tech"),
            ChoreIcon("Key", "Key", R.drawable.ic_chore_key, "lock secure"),
            ChoreIcon("FormatPaint", "Paint", R.drawable.ic_chore_format_paint, "decorate"),
            ChoreIcon("Thermostat", "Thermostat", R.drawable.ic_chore_thermostat, "temperature"),
            ChoreIcon("Hvac", "HVAC", R.drawable.ic_chore_hvac, "filter air conditioning"),
            ChoreIcon("Air", "Air filter", R.drawable.ic_chore_air, "ventilation"),
            ChoreIcon("Fireplace", "Fireplace", R.drawable.ic_chore_fireplace, "chimney"),
            ChoreIcon("Chair", "Furniture", R.drawable.ic_chore_chair, "chair"),
            ChoreIcon("Weekend", "Sofa", R.drawable.ic_chore_weekend, "couch furniture"),
            ChoreIcon("Inventory", "Storage", R.drawable.ic_chore_inventory_2, "organize boxes"),
        ),
    ),
    IconGroup(
        "Outdoors & Garden",
        listOf(
            ChoreIcon("Grass", "Lawn", R.drawable.ic_chore_grass, "mow garden yard"),
            ChoreIcon("Yard", "Yard", R.drawable.ic_chore_yard, "garden outdoor"),
            ChoreIcon("Forest", "Plants", R.drawable.ic_chore_forest, "trees garden"),
            ChoreIcon("Favorite", "Plant care", R.drawable.ic_chore_favorite, "love care plant"),
            ChoreIcon("LocationOn", "Outdoor", R.drawable.ic_chore_location_on, "location garden yard"),
            ChoreIcon("DirectionsCar", "Car", R.drawable.ic_chore_directions_car, "wash vehicle"),
            ChoreIcon("LocalGasStation", "Fuel", R.drawable.ic_chore_local_gas_station, "gas car"),
            ChoreIcon("Umbrella", "Weather", R.drawable.ic_chore_umbrella, "outdoor rain"),
        ),
    ),
    IconGroup(
        "Pets",
        listOf(
            ChoreIcon("Pets", "Pets", R.drawable.ic_chore_pets, "dog cat feed"),
            ChoreIcon("CrueltyFree", "Animal care", R.drawable.ic_chore_cruelty_free, "pet rabbit"),
        ),
    ),
    IconGroup(
        "Health & Self-care",
        listOf(
            ChoreIcon("Medication", "Medication", R.drawable.ic_chore_medication, "pills health"),
            ChoreIcon("FitnessCenter", "Exercise", R.drawable.ic_chore_fitness_center, "gym workout"),
            ChoreIcon("SelfImprovement", "Wellness", R.drawable.ic_chore_self_improvement, "meditate self-care"),
            ChoreIcon("Face", "Grooming", R.drawable.ic_chore_face, "personal grooming"),
            ChoreIcon("ChildCare", "Childcare", R.drawable.ic_chore_child_care, "baby kids"),
            ChoreIcon("Elderly", "Elder care", R.drawable.ic_chore_elderly, "care"),
            ChoreIcon("VolunteerActivism", "Care", R.drawable.ic_chore_volunteer_activism, "help support"),
        ),
    ),
    IconGroup(
        "Money & Admin",
        listOf(
            ChoreIcon("ShoppingCart", "Shopping", R.drawable.ic_chore_shopping_cart, "groceries buy store"),
            ChoreIcon("ShoppingBasket", "Groceries", R.drawable.ic_chore_shopping_basket, "shopping"),
            ChoreIcon("Payments", "Bills", R.drawable.ic_chore_payments, "pay money"),
            ChoreIcon("ReceiptLong", "Receipts", R.drawable.ic_chore_receipt_long, "bills invoice"),
            ChoreIcon("Savings", "Savings", R.drawable.ic_chore_savings, "budget money"),
            ChoreIcon("Mail", "Mail", R.drawable.ic_chore_mail, "post letters"),
            ChoreIcon("CalendarMonth", "Calendar", R.drawable.ic_chore_calendar_month, "date schedule month"),
            ChoreIcon("Alarm", "Reminder", R.drawable.ic_chore_alarm, "alert"),
            ChoreIcon("Notifications", "Bell", R.drawable.ic_chore_notifications, "remind alert"),
            ChoreIcon("Checklist", "Checklist", R.drawable.ic_chore_checklist, "tasks list"),
        ),
    ),
    IconGroup(
        "Study & Work",
        listOf(
            ChoreIcon("School", "Study", R.drawable.ic_chore_school, "learn homework"),
            ChoreIcon("MenuBook", "Reading", R.drawable.ic_chore_menu_book, "book study"),
            ChoreIcon("EditNote", "Notes", R.drawable.ic_chore_edit_note, "write homework"),
            ChoreIcon("LaptopMac", "Laptop", R.drawable.ic_chore_laptop_mac, "computer work"),
            ChoreIcon("Devices", "Devices", R.drawable.ic_chore_devices, "tech electronics"),
            ChoreIcon("Router", "Router", R.drawable.ic_chore_router, "wifi internet"),
            ChoreIcon("Wifi", "Wifi", R.drawable.ic_chore_wifi, "internet network"),
            ChoreIcon("SmartToy", "Tech", R.drawable.ic_chore_smart_toy, "gadget robot"),
            ChoreIcon("MusicNote", "Music", R.drawable.ic_chore_music_note, "practice instrument"),
            ChoreIcon("SportsEsports", "Gaming", R.drawable.ic_chore_sports_esports, "games"),
        ),
    ),
    IconGroup(
        "General",
        listOf(
            ChoreIcon("CheckCircle", "Check", R.drawable.ic_chore_check_circle, "done complete tick"),
            ChoreIcon("Home", "Home", R.drawable.ic_chore_home, "house room"),
            ChoreIcon("Refresh", "Replace", R.drawable.ic_chore_refresh, "change cycle renew rotate"),
            ChoreIcon("Star", "Star", R.drawable.ic_chore_star, "important favourite"),
            ChoreIcon("Person", "Person", R.drawable.ic_chore_person, "self me"),
            ChoreIcon("Settings", "Settings", R.drawable.ic_chore_settings, "gear machine appliance"),
            ChoreIcon("Lock", "Lock", R.drawable.ic_chore_lock, "secure door key"),
            ChoreIcon("Call", "Phone", R.drawable.ic_chore_call, "call ring"),
            ChoreIcon("Warning", "Warning", R.drawable.ic_chore_warning, "alert caution urgent"),
            ChoreIcon("Info", "Info", R.drawable.ic_chore_info, "note detail"),
            ChoreIcon("Search", "Search", R.drawable.ic_chore_search, "find inspect check"),
            ChoreIcon("Share", "Share", R.drawable.ic_chore_share, "send"),
            ChoreIcon("ThumbUp", "Thumb up", R.drawable.ic_chore_thumb_up, "good ok"),
            ChoreIcon("AccountBox", "Box", R.drawable.ic_chore_account_box, "storage container"),
            ChoreIcon("Add", "Add", R.drawable.ic_chore_add, "new plus"),
            ChoreIcon("Close", "Clear", R.drawable.ic_chore_close, "clean wipe remove"),
        ),
    ),
)

val CURATED_ICONS: List<ChoreIcon> = ICON_GROUPS.flatMap { it.icons }

private val ICONS_BY_KEY = CURATED_ICONS.associateBy { it.key }

/**
 * Pre-expansion keys persisted by the original 26-icon catalog, mapped to their
 * nearest replacement so chores created before this change keep their icon.
 * Most old keys are unchanged; only the handful backed by a renamed or dropped
 * drawable need an alias.
 */
private val LEGACY_KEY_ALIASES: Map<String, String> = mapOf(
    "DateRange" to "CalendarMonth",
    "Email" to "Mail",
    "List" to "Checklist",
    "Place" to "LocationOn",
    "Clear" to "Close",
)

private fun resolveKey(key: String): String = LEGACY_KEY_ALIASES[key] ?: key

/** Resolves a persisted key, falling back so an unknown key never crashes. */
@Composable
fun iconForKey(key: String): ImageVector {
    val icon = ICONS_BY_KEY[resolveKey(key)]
    return if (icon != null) {
        ImageVector.vectorResource(icon.res)
    } else {
        Icons.Filled.CheckCircle
    }
}

fun searchIcons(query: String): List<IconGroup> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return ICON_GROUPS
    return ICON_GROUPS.mapNotNull { group ->
        val matches = group.icons.filter {
            it.label.lowercase().contains(q) || it.keywords.contains(q)
        }
        if (matches.isEmpty()) null else group.copy(icons = matches)
    }
}

/**
 * Emoji suggestions for the Emoji tab. The user can also type any emoji with the
 * system keyboard -- FR-4 explicitly prefers that over a custom grid, so this is
 * just a shortcut row for common chores.
 */
val SUGGESTED_EMOJI: List<String> = listOf(
    "🛏️", "🧺", "🧻", "🚿", "🛁", "🚽", "🪠", "🧼", "🧽", "🧹",
    "🧴", "🗑️", "♻️", "🪣", "🧊", "🍽️", "🍳", "🥘", "☕", "🌱",
    "🪴", "🌳", "🚗", "🔧", "🔩", "🪛", "💡", "🔋", "🧯", "🪟",
    "🚪", "🐕", "🐈", "🐟", "👕", "👟", "💊", "📬", "💸", "📅",
)
