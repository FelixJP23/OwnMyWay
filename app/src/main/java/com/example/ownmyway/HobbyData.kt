package com.example.ownmyway

object HobbyData {

    data class Hobby(val displayName: String, val placeTypes: List<Pair<String, Double>>)

    val ALL_HOBBIES = listOf(
        Hobby("Desenho / esboço",                              listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("Pintura (aquarela, acrílica, digital)",         listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("Fotografia",                                    listOf("tourist_attraction" to 2.0, "park" to 1.5, "museum" to 1.0)),
        Hobby("Escrita (contos, blogs, poesia)",               listOf("cafe" to 2.0, "park" to 1.5, "library" to 1.0)),
        Hobby("Tocar um instrumento musical",                  listOf("bar" to 1.5, "night_club" to 1.0)),
        Hobby("Produção musical / criação de beats",           listOf("bar" to 1.5, "night_club" to 1.5)),
        Hobby("Design gráfico",                                listOf("art_gallery" to 2.0, "museum" to 1.5)),
        Hobby("Artesanato (origami, tricô, bijuteria)",        listOf("shopping_mall" to 1.5, "market" to 2.0)),
        Hobby("Programação",                                   listOf("cafe" to 2.5)),
        Hobby("Desenvolvimento de jogos",                      listOf("cafe" to 2.0, "amusement_park" to 1.0)),
        Hobby("Academia / musculação",                         listOf("gym" to 2.5)),
        Hobby("Corrida",                                       listOf("park" to 2.5)),
        Hobby("Ciclismo",                                      listOf("park" to 2.5)),
        Hobby("Natação",                                       listOf("spa" to 2.0)),
        Hobby("Trilha / trekking",                             listOf("park" to 2.5, "natural_feature" to 2.0)),
        Hobby("Cozinhar / confeitar",                          listOf("restaurant" to 2.0, "food" to 1.5)),
        Hobby("Viajar / explorar novos lugares",               listOf("tourist_attraction" to 2.5, "museum" to 1.5)),
        Hobby("Voluntariado",                                  listOf("park" to 1.5, "church" to 1.0)),
        Hobby("Organização de eventos",                        listOf("stadium" to 1.5, "event_venue" to 2.0)),
        Hobby("Dançar",                                        listOf("night_club" to 2.5)),
        Hobby("Leitura",                                       listOf("library" to 2.0, "cafe" to 2.0, "park" to 1.5)),
        Hobby("Diário pessoal",                                listOf("cafe" to 2.5, "park" to 2.0)),
        Hobby("Meditação",                                     listOf("spa" to 2.0, "park" to 2.5)),
        Hobby("Jardinagem",                                    listOf("park" to 2.5, "zoo" to 1.5))
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
