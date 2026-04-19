package com.example.ownmyway

object HobbyData {

    data class Hobby(val displayName: String, val placeTypes: List<Pair<String, Double>>)

    val ALL_HOBBIES = listOf(
        Hobby("Drawing / sketching",                  listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("Painting (watercolor, acrylic, digital)", listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("Photography",                          listOf("tourist_attraction" to 2.0, "park" to 1.5, "museum" to 1.0)),
        Hobby("Writing (stories, blogs, poetry)",     listOf("cafe" to 2.0, "park" to 1.5, "library" to 1.0)),
        Hobby("Playing a musical instrument",         listOf("bar" to 1.5, "night_club" to 1.0)),
        Hobby("Music production / beat making",       listOf("bar" to 1.5, "night_club" to 1.5)),
        Hobby("Graphic design",                       listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("DIY crafts (origami, knitting, jewelry making)", listOf("shopping_mall" to 1.5, "market" to 2.0)),
        Hobby("Programming / coding",                 listOf("cafe" to 2.5)),
        Hobby("Game development",                     listOf("cafe" to 2.0, "amusement_park" to 1.0)),
        Hobby("Gym / weightlifting",                  listOf("gym" to 2.5)),
        Hobby("Running / jogging",                    listOf("park" to 2.5)),
        Hobby("Cycling",                              listOf("park" to 2.5)),
        Hobby("Swimming",                             listOf("spa" to 2.0)),
        Hobby("Hiking / trekking",                    listOf("park" to 2.5, "natural_feature" to 2.0)),
        Hobby("Cooking / baking",                     listOf("restaurant" to 2.0, "food" to 1.5)),
        Hobby("Traveling / exploring new places",     listOf("tourist_attraction" to 2.5, "museum" to 1.5)),
        Hobby("Volunteering",                         listOf("park" to 1.5, "church" to 1.0)),
        Hobby("Event organizing",                     listOf("stadium" to 1.5, "event_venue" to 2.0)),
        Hobby("Dancing",                              listOf("night_club" to 2.5)),
        Hobby("Reading",                              listOf("library" to 2.0, "cafe" to 2.0, "park" to 1.5)),
        Hobby("Journaling",                           listOf("cafe" to 2.5, "park" to 2.0)),
        Hobby("Meditation",                           listOf("spa" to 2.0, "park" to 2.5)),
        Hobby("Gardening",                            listOf("park" to 2.5, "zoo" to 1.5))
    )

    // Returns map of placeType → total weight for selected hobbies
    fun getPlaceWeights(selectedHobbies: List<String>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
        ALL_HOBBIES
            .filter { it.displayName in selectedHobbies }
            .forEach { hobby ->
                hobby.placeTypes.forEach { (type, weight) ->
                    weights[type] = (weights[type] ?: 0.0) + weight
                }
            }
        // Fallback: if no hobbies selected, use general tourist attractions
        if (weights.isEmpty()) {
            weights["tourist_attraction"] = 1.5
            weights["museum"] = 1.0
            weights["park"] = 1.0
        }
        return weights
    }
}
