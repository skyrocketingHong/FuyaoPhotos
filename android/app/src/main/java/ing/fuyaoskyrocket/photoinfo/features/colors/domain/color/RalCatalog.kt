package ing.fuyaoskyrocket.photoinfo.features.colors.domain.color

import android.graphics.Color
import ing.fuyaoskyrocket.photoinfo.features.colors.domain.model.RalMatch

object RalCatalog {
    private data class RalColor(
        val code: Int,
        val red: Int,
        val green: Int,
        val blue: Int,
        val name: String,
    )

    private val colors: List<RalColor> by lazy {
        rawEntries.lineSequence()
            .filter(String::isNotBlank)
            .map { line ->
                val values = line.split(",", limit = 5)
                RalColor(
                    code = values[0].toInt(),
                    red = values[1].toInt(),
                    green = values[2].toInt(),
                    blue = values[3].toInt(),
                    name = values[4],
                )
            }
            .toList()
    }

    fun nearestTo(sampledColor: Int): RalMatch {
        val sampleRed = Color.red(sampledColor)
        val sampleGreen = Color.green(sampledColor)
        val sampleBlue = Color.blue(sampledColor)

        val nearest = colors.minBy { color ->
            val redDifference = color.red - sampleRed
            val greenDifference = color.green - sampleGreen
            val blueDifference = color.blue - sampleBlue
            redDifference * redDifference +
                greenDifference * greenDifference +
                blueDifference * blueDifference
        }

        return RalMatch(
            code = nearest.code,
            name = nearest.name,
            red = nearest.red,
            green = nearest.green,
            blue = nearest.blue,
        )
    }

    // RAL reference values recovered from the supplied legacy APK.
    private val rawEntries = """
1000,190,189,127,Green beige
1001,194,176,120,Beige
1002,198,166,100,Sand yellow
1003,229,190,1,Signal yellow
1004,205,164,52,Golden yellow
1005,169,131,7,Honey yellow
1006,228,160,16,Maize yellow
1007,220,156,0,Daffodil yellow
1011,138,102,66,Brown beige
1012,199,180,70,Lemon yellow
1013,234,230,202,Oyster white
1014,225,204,79,Ivory
1015,230,214,144,Light ivory
1016,237,255,33,Sulfur yellow
1017,245,208,51,Saffron yellow
1018,248,243,53,Zinc yellow
1019,158,151,100,Grey beige
1020,153,153,80,Olive yellow
1021,243,218,11,Rape yellow
1023,250,210,1,Traffic yellow
1024,174,160,75,Ochre yellow
1026,255,255,0,Luminous yellow
1027,157,145,1,Curry
1028,244,169,0,Melon yellow
1032,214,174,1,Broom yellow
1033,243,165,5,Dahlia yellow
1034,239,169,74,Pastel yellow
1035,106,93,77,Pearl beige
1036,112,83,53,Pearl gold
1037,243,159,24,Sun yellow
2000,237,118,14,Yellow orange
2001,201,60,32,Red orange
2002,203,40,33,Vermilion
2003,255,117,20,Pastel orange
2004,244,70,17,Pure orange
2005,255,35,1,Luminous orange
2007,255,164,32,Luminous bright orange
2008,247,94,37,Bright red orange
2009,245,64,33,Traffic orange
2010,216,75,32,Signal orange
2011,236,124,38,Deep orange
2012,235,106,14,Salmon range
2013,195,88,49,Pearl orange
3000,175,43,30,Flame red
3001,165,32,25,Signal red
3002,162,35,29,Carmine red
3003,155,17,30,Ruby red
3004,117,21,30,Purple red
3005,94,33,41,Wine red
3007,65,34,39,Black red
3009,100,36,36,Oxide red
3011,120,31,25,Brown red
3012,193,135,107,Beige red
3013,161,35,18,Tomato red
3014,211,110,112,Antique pink
3015,234,137,154,Light pink
3016,179,40,33,Coral red
3017,230,50,68,Rose
3018,213,48,50,Strawberry red
3020,204,6,5,Traffic red
3022,217,80,48,Salmon pink
3024,248,0,0,Luminous red
3026,254,0,0,Luminous bright red
3027,197,29,52,Raspberry red
3028,255,0,0,Pure  red
3031,179,36,40,Orient red
3032,114,20,34,Pearl ruby red
3033,180,76,67,Pearl pink
4001,222,76,138,Red lilac
4002,146,43,62,Red violet
4003,222,76,138,Heather violet
4004,110,28,52,Claret violet
4005,108,70,117,Blue lilac
4006,160,52,114,Traffic purple
4007,74,25,44,Purple violet
4008,146,78,125,Signal violet
4009,164,125,144,Pastel violet
4010,215,45,109,Telemagenta
4011,134,115,161,Pearl violet
4012,108,104,129,Pearl black berry
5000,42,46,75,Violet blue
5001,31,52,56,Green blue
5002,32,33,79,Ultramarine blue
5003,29,30,51,Saphire blue
5004,32,33,79,Black blue
5005,30,45,110,Signal blue
5007,62,95,138,Brillant blue
5008,38,37,45,Grey blue
5009,2,86,105,Azure blue
5010,14,41,75,Gentian blue
5011,35,26,36,Steel blue
5012,59,131,189,Light blue
5013,37,41,74,Cobalt blue
5014,96,111,140,Pigeon blue
5015,34,113,179,Sky blue
5017,6,57,113,Traffic blue
5018,63,136,143,Turquoise blue
5019,27,85,131,Capri blue
5020,29,51,74,Ocean blue
5021,37,109,123,Water blue
5022,37,40,80,Night blue
5023,73,103,141,Distant blue
5024,93,155,155,Pastel blue
5025,42,100,120,Pearl gentian blue
5026,16,44,84,Pearl night blue
6000,49,102,80,Patina green
6001,40,114,51,Emerald green
6002,45,87,44,Leaf green
6003,66,70,50,Olive green
6004,31,58,61,Blue green
6005,47,69,56,Moss green
6006,62,59,50,Grey olive
6007,52,59,41,Bottle green
6008,57,53,42,Brown green
6009,49,55,43,Fir green
6010,53,104,45,Grass green
6011,88,114,70,Reseda green
6012,52,62,64,Black green
6013,108,113,86,Reed green
6014,71,64,46,Yellow olive
6015,59,60,54,Black olive
6016,30,89,69,Turquoise green
6017,76,145,65,May green
6018,87,166,57,Yellow green
6019,189,236,182,Pastel green
6020,46,58,35,Chrome green
6021,137,172,118,Pale green
6022,37,34,27,Olive drab
6024,48,132,70,Traffic green
6025,61,100,45,Fern green
6026,1,93,82,Opal green
6027,132,195,190,Light green
6028,44,85,69,Pine green
6029,32,96,61,Mint green
6032,49,127,67,Signal green
6033,73,126,118,Mint turquoise
6034,127,181,181,Pastel turquoise
6035,28,84,45,Pearl green
6036,22,53,55,Pearl opal green
6037,0,255,0,Pure green
6038,0,248,0,Luminous green
7000,120,133,139,Squirrel grey
7001,138,149,151,Silver grey
7002,126,123,82,Olive grey
7003,108,112,89,Moss grey
7004,150,153,146,Signal grey
7005,100,107,99,Mouse grey
7006,109,101,82,Beige grey
7008,106,95,49,Khaki grey
7009,77,86,69,Green grey
7010,76,81,74,Tarpaulin grey
7011,67,75,77,Iron grey
7012,78,87,84,Basalt grey
7013,70,69,49,Brown grey
7015,67,71,80,Slate grey
7016,41,49,51,Anthracite grey
7021,35,40,43,Black grey
7022,51,47,44,Umbra grey
7023,104,108,94,Concrete grey
7024,71,74,81,Graphite grey
7026,47,53,59,Granite grey
7030,139,140,122,Stone grey
7031,71,75,78,Blue grey
7032,184,183,153,Pebble grey
7033,125,132,113,Cement grey
7034,143,139,102,Yellow grey
7035,215,215,215,Light grey
7036,127,118,121,Platinum grey
7037,125,127,120,Dusty grey
7038,195,195,195,Agate grey
7039,108,105,96,Quartz grey
7040,157,161,170,Window grey
7042,141,148,141,Traffic grey A
7043,78,84,82,Traffic grey B
7044,202,196,176,Silk grey
7045,144,144,144,Telegrey 1
7046,130,137,143,Telegrey 2
7047,208,208,208,Telegrey 4
7048,137,129,118,Pearl mouse grey
8000,130,108,52,Green brown
8001,149,95,32,Ochre brown
8002,108,59,42,Signal brown
8003,115,66,34,Clay brown
8004,142,64,42,Copper brown
8007,89,53,31,Fawn brown
8008,111,79,40,Olive brown
8011,91,58,41,Nut brown
8012,89,35,33,Red brown
8014,56,44,30,Sepia brown
8015,99,58,52,Chestnut brown
8016,76,47,39,Mahogany brown
8017,69,50,46,Chocolate brown
8019,64,58,58,Grey brown
8022,33,33,33,Black brown
8023,166,94,46,Orange brown
8024,121,85,61,Beige brown
8025,117,92,72,Pale brown
8028,78,59,49,Terra brown
8029,118,60,40,Pearl copper
9001,250,244,227,Cream
9002,231,235,218,Grey white
9003,244,244,244,Signal white
9004,40,40,40,Signal black
9005,10,10,10,Jet black
9006,165,165,165,White aluminium
9007,143,143,143,Grey aluminium
9010,255,255,255,Pure white
9011,28,28,28,Graphite black
9016,246,246,246,Traffic white
9017,30,30,30,Traffic black
9018,215,215,215,Papyrus white
9022,156,156,156,Pearl light grey
9023,130,130,130,RAL 9023
    """.trimIndent()
}
