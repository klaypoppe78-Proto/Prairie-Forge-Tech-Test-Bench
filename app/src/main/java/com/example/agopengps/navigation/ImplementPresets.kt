package com.example.agopengps.navigation

/**
 * Predefined Implement Profile Preset for quick 1-tap machine switching.
 */
data class ImplementPreset(
    val id: String,
    val name: String,
    val implementType: ImplementType,
    val toolWidthMeters: Double,
    val numSections: Int,
    val offsetBehindTractorMeters: Double = 0.914,
    val overlapMeters: Double = 0.15,
    val lateralOffsetMeters: Double = 0.0,
    val isAutoSectionControl: Boolean = true,
    val category: String,
    val description: String
) {
    val toolWidthFeet: Double get() = toolWidthMeters * 3.28084

    fun toImplementConfig(): ImplementConfig {
        return ImplementConfig(
            implementType = implementType,
            toolWidth = toolWidthMeters,
            offsetBehindTractor = offsetBehindTractorMeters,
            numSections = numSections,
            overlap = overlapMeters,
            lateralOffset = lateralOffsetMeters,
            isMasterActive = true,
            isAutoSectionControl = isAutoSectionControl
        )
    }

    companion object {
        fun getAllPresets(): List<ImplementPreset> = FACTORY_PRESETS

        val FACTORY_PRESETS = listOf(
            ImplementPreset(
                id = "planter_12r22",
                name = "12-Row 22\" Planter (22.0 ft)",
                implementType = ImplementType.THREE_POINT_MOUNTED,
                toolWidthMeters = 6.7056, // 12 * 22 inches = 264 in = 22.0 ft = 6.7056 m
                numSections = 12,
                offsetBehindTractorMeters = 1.20,
                overlapMeters = 0.08,
                category = "Planting & Seeding",
                description = "12-row 22-inch spacing with individual row shutoff clutches (22 ft total width)"
            ),
            ImplementPreset(
                id = "planter_24r22",
                name = "24-Row 22\" Planter (44.0 ft)",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 13.4112, // 24 * 22 inches = 528 in = 44.0 ft = 13.4112 m
                numSections = 24,
                offsetBehindTractorMeters = 3.50,
                overlapMeters = 0.08,
                category = "Planting & Seeding",
                description = "24-row 22-inch central-fill toolbar with individual row section control (44 ft total width)"
            ),
            ImplementPreset(
                id = "sugarbeet_12r22",
                name = "12-Row 22\" Beet Harvester / Defoliator (22 ft)",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 6.7056, // 22.0 ft
                numSections = 12,
                offsetBehindTractorMeters = 3.20,
                overlapMeters = 0.05,
                category = "Harvest",
                description = "12-row 22-inch lifter-loader / topper harvesting setup"
            ),
            ImplementPreset(
                id = "cultivator_24r22",
                name = "24-Row 22\" Strip-Till / Row Cultivator (44 ft)",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 13.4112, // 44.0 ft
                numSections = 24,
                offsetBehindTractorMeters = 3.80,
                overlapMeters = 0.10,
                category = "Tillage",
                description = "24-row 22-inch deep-banding nutrient placement & row cultivator"
            ),
            ImplementPreset(
                id = "planter_16r30",
                name = "16-Row 30\" Planter (40 ft)",
                implementType = ImplementType.THREE_POINT_MOUNTED,
                toolWidthMeters = 12.192, // 40.0 ft
                numSections = 16,
                offsetBehindTractorMeters = 1.20,
                overlapMeters = 0.10,
                category = "Planting & Seeding",
                description = "16 row-units with individual row clutches / section shutoff"
            ),
            ImplementPreset(
                id = "planter_24r30",
                name = "24-Row 30\" Planter (60 ft)",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 18.288, // 60.0 ft
                numSections = 24,
                offsetBehindTractorMeters = 3.50,
                overlapMeters = 0.10,
                category = "Planting & Seeding",
                description = "24-row central-fill trailing planter with individual hydraulic downforce"
            ),
            ImplementPreset(
                id = "sprayer_60ft",
                name = "60 ft 3-Point Sprayer",
                implementType = ImplementType.THREE_POINT_MOUNTED,
                toolWidthMeters = 18.288, // 60.0 ft
                numSections = 5,
                offsetBehindTractorMeters = 0.90,
                overlapMeters = 0.30,
                category = "Spraying & Chemical",
                description = "5-section mounted boom sprayer with 15-inch nozzle spacing"
            ),
            ImplementPreset(
                id = "sprayer_90ft",
                name = "90 ft Pull-Type Sprayer",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 27.432, // 90.0 ft
                numSections = 7,
                offsetBehindTractorMeters = 4.20,
                overlapMeters = 0.35,
                category = "Spraying & Chemical",
                description = "7-section trailing sprayer with active boom height sensing"
            ),
            ImplementPreset(
                id = "sprayer_120ft",
                name = "120 ft High-Clearance Sprayer",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 36.576, // 120.0 ft
                numSections = 10,
                offsetBehindTractorMeters = 3.80,
                overlapMeters = 0.40,
                category = "Spraying & Chemical",
                description = "10-section wide boom sprayer with PWM nozzle pulsing control"
            ),
            ImplementPreset(
                id = "cultivator_35ft",
                name = "35 ft Field Cultivator",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 10.668, // 35.0 ft
                numSections = 1,
                offsetBehindTractorMeters = 4.00,
                overlapMeters = 0.25,
                category = "Tillage",
                description = "Single-section wide spring-shank secondary tillage cultivator"
            ),
            ImplementPreset(
                id = "disk_45ft",
                name = "45 ft High-Speed Disk / Vertical Tillage",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 13.716, // 45.0 ft
                numSections = 1,
                offsetBehindTractorMeters = 4.50,
                overlapMeters = 0.30,
                category = "Tillage",
                description = "Heavy primary/secondary residue management disk harrow"
            ),
            ImplementPreset(
                id = "corn_head_12r30",
                name = "12-Row 30\" Corn Head (30 ft)",
                implementType = ImplementType.THREE_POINT_MOUNTED,
                toolWidthMeters = 9.144, // 30.0 ft
                numSections = 1,
                offsetBehindTractorMeters = 0.50,
                overlapMeters = 0.05,
                category = "Harvest",
                description = "12-row rigid/folding corn harvesting header"
            ),
            ImplementPreset(
                id = "grain_drill_30ft",
                name = "30 ft No-Till Air Drill / Seeder",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 9.144, // 30.0 ft
                numSections = 4,
                offsetBehindTractorMeters = 3.60,
                overlapMeters = 0.15,
                category = "Planting & Seeding",
                description = "4-rank no-till small grains and soybean air seeder"
            ),
            ImplementPreset(
                id = "grain_cart_1100bu",
                name = "Grain Cart Hauling (1,100 Bu)",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 3.6576, // 12.0 ft
                numSections = 1,
                offsetBehindTractorMeters = 4.80,
                overlapMeters = 0.05,
                category = "Harvest & Hauling",
                description = "1,100 Bushel grain cart hauling & staging tracking profile with high-clearance wheel paths"
            ),
            ImplementPreset(
                id = "hauling_tandem_wagon",
                name = "Tandem Grain Hauling Wagon / Semi",
                implementType = ImplementType.TRAILING_TOOLBAR,
                toolWidthMeters = 3.048, // 10.0 ft
                numSections = 1,
                offsetBehindTractorMeters = 6.20,
                overlapMeters = 0.05,
                category = "Harvest & Hauling",
                description = "Field-to-bin road hauling & headland staging corridor navigation"
            )
        )
    }
}
