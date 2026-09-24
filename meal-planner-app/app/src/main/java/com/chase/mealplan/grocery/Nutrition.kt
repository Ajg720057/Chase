package com.chase.mealplan.grocery

import kotlin.math.roundToInt

/** Calories and macros. Used both per 100 g (food table) and per serving (meals). */
data class NutritionFacts(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: NutritionFacts) =
        NutritionFacts(calories + o.calories, protein + o.protein, carbs + o.carbs, fat + o.fat)

    operator fun times(k: Double) = NutritionFacts(calories * k, protein * k, carbs * k, fat * k)
    operator fun div(k: Double) = times(1.0 / k)

    /** "520 cal · 32 g protein · 45 g carbs · 18 g fat" */
    fun summary(): String =
        "${calories.roundToInt()} cal · ${protein.roundToInt()} g protein · " +
            "${carbs.roundToInt()} g carbs · ${fat.roundToInt()} g fat"
}

/**
 * A food in the built-in table. Values are per 100 g, rounded from USDA figures.
 * [cup] is grams in one cup, [each] grams in one whole item, [units] grams in other
 * units such as a slice or a can.
 */
data class Food(
    val per100g: NutritionFacts,
    val cup: Double? = null,
    val each: Double? = null,
    val units: Map<String, Double> = emptyMap(),
) {
    val negligible get() = per100g.calories == 0.0
}

/** Result of adding up a recipe's ingredients. [missing] lists what couldn't be counted. */
data class NutritionEstimate(val total: NutritionFacts, val counted: Int, val missing: List<String>)

object Nutrition {
    private fun f(
        aliases: String, kcal: Double, protein: Double, carbs: Double, fat: Double,
        cup: Double? = null, each: Double? = null, units: String = "",
    ): List<Pair<String, Food>> {
        val unitMap = units.split(',').filter { '=' in it }.associate {
            val (u, g) = it.split('=')
            u.trim() to g.trim().toDouble()
        }
        val food = Food(NutritionFacts(kcal, protein, carbs, fat), cup, each, unitMap)
        return aliases.split('|').map { it.trim() to food }
    }

    /** Seasonings and the like: known, but too small to count. */
    private fun zero(aliases: String) = f(aliases, 0.0, 0.0, 0.0, 0.0)

    private val foods: List<Pair<String, Food>> = listOf(
        // Meat, fish, eggs, tofu
        f("chicken breast|boneless chicken breast|chicken tender|chicken tenderloin", 120.0, 22.5, 0.0, 2.6, cup = 140.0, each = 170.0),
        f("chicken thigh|boneless chicken thigh", 121.0, 19.7, 0.0, 4.1, each = 110.0, cup = 140.0),
        f("chicken|whole chicken|chicken leg|drumstick|chicken wing", 143.0, 18.0, 0.0, 8.0, each = 150.0, cup = 140.0),
        f("cooked chicken|shredded chicken|rotisserie chicken|canned chicken", 190.0, 29.0, 0.0, 7.5, cup = 140.0),
        f("ground beef|hamburger|hamburger meat", 254.0, 17.2, 0.0, 20.0, cup = 225.0),
        f("lean ground beef|extra lean ground beef", 176.0, 20.0, 0.0, 10.0, cup = 225.0),
        f("beef|steak|sirloin|sirloin steak|ribeye|flank steak|skirt steak|stew meat", 160.0, 21.0, 0.0, 8.0, each = 250.0),
        f("chuck roast|pot roast|roast|brisket|short rib|chuck", 200.0, 19.0, 0.0, 13.0),
        f("ground turkey", 150.0, 19.7, 0.0, 8.3, cup = 225.0),
        f("turkey|turkey breast|deli turkey|sliced turkey|deli meat|lunch meat", 104.0, 17.0, 4.0, 1.7, units = "slice=28"),
        f("pork chop|pork|pork loin|pork tenderloin|tenderloin", 143.0, 21.0, 0.0, 6.0, each = 170.0),
        f("pork shoulder|pork butt", 180.0, 17.0, 0.0, 12.0),
        f("ground pork", 263.0, 17.0, 0.0, 21.0, cup = 225.0),
        f("bacon|turkey bacon", 541.0, 37.0, 1.4, 42.0, each = 8.0, units = "slice=8,strip=8"),
        f("sausage|italian sausage|breakfast sausage|bratwurst|kielbasa|chorizo|hot dog", 300.0, 12.0, 1.0, 27.0, each = 75.0),
        f("ham", 145.0, 21.0, 1.5, 6.0, cup = 140.0, units = "slice=28"),
        f("pepperoni|salami", 494.0, 23.0, 1.2, 44.0, units = "slice=2"),
        f("salmon", 208.0, 20.0, 0.0, 13.0, each = 170.0, units = "fillet=170"),
        f("tuna|canned tuna", 116.0, 26.0, 0.0, 1.0, cup = 150.0, units = "can=113"),
        f("cod|tilapia|white fish|fish|halibut|mahi mahi|catfish|trout", 90.0, 20.0, 0.0, 1.0, each = 150.0, units = "fillet=150"),
        f("shrimp|prawn|scallop", 85.0, 20.0, 0.0, 0.5, cup = 145.0),
        f("tofu", 144.0, 17.3, 2.8, 8.7, cup = 252.0, each = 400.0, units = "package=400,block=400"),
        f("egg", 143.0, 12.6, 0.7, 9.5, each = 50.0, cup = 243.0),
        f("egg white", 52.0, 11.0, 0.7, 0.2, each = 33.0, cup = 243.0),

        // Dairy
        f("milk|2% milk|reduced fat milk", 50.0, 3.3, 4.8, 2.0, cup = 244.0),
        f("whole milk", 61.0, 3.2, 4.8, 3.3, cup = 244.0),
        f("skim milk|nonfat milk", 34.0, 3.4, 5.0, 0.1, cup = 245.0),
        f("almond milk|oat milk|soy milk", 30.0, 1.0, 3.0, 1.5, cup = 240.0),
        f("buttermilk", 40.0, 3.3, 4.8, 0.9, cup = 245.0),
        f("heavy cream|whipping cream|heavy whipping cream|cream", 340.0, 2.8, 2.7, 36.0, cup = 238.0),
        f("half and half", 131.0, 3.0, 4.3, 11.5, cup = 242.0),
        f("sour cream", 198.0, 2.4, 4.6, 19.0, cup = 230.0),
        f("butter|unsalted butter|salted butter|margarine", 717.0, 0.9, 0.1, 81.0, cup = 227.0, units = "stick=113"),
        f("cheese|cheddar|cheddar cheese|shredded cheese|mexican cheese|colby|monterey jack|pepper jack|american cheese|swiss|swiss cheese|provolone|gouda", 403.0, 25.0, 1.3, 33.0, cup = 113.0, units = "slice=21"),
        f("mozzarella", 280.0, 28.0, 3.0, 17.0, cup = 112.0, units = "slice=21"),
        f("parmesan|parmigiano", 431.0, 38.0, 4.0, 29.0, cup = 100.0),
        f("cream cheese", 342.0, 6.0, 4.0, 34.0, cup = 232.0, units = "package=227,block=227"),
        f("cottage cheese", 98.0, 11.0, 3.4, 4.3, cup = 226.0),
        f("ricotta", 174.0, 11.0, 3.0, 13.0, cup = 246.0),
        f("feta|goat cheese", 264.0, 14.0, 4.0, 21.0, cup = 150.0),
        f("greek yogurt", 59.0, 10.0, 3.6, 0.4, cup = 245.0, each = 170.0),
        f("yogurt", 61.0, 3.5, 4.7, 3.3, cup = 245.0, each = 170.0),
        f("ice cream", 207.0, 3.5, 24.0, 11.0, cup = 132.0),

        // Grains, bread, pantry
        f("flour|all purpose flour|bread flour|self rising flour", 364.0, 10.0, 76.0, 1.0, cup = 125.0),
        f("whole wheat flour", 340.0, 13.0, 72.0, 2.5, cup = 120.0),
        f("sugar|white sugar|granulated sugar|cane sugar", 387.0, 0.0, 100.0, 0.0, cup = 200.0),
        f("brown sugar", 380.0, 0.1, 98.0, 0.0, cup = 220.0),
        f("powdered sugar|confectioner sugar", 389.0, 0.0, 100.0, 0.0, cup = 120.0),
        f("honey", 304.0, 0.3, 82.0, 0.0, cup = 340.0),
        f("maple syrup|syrup|pancake syrup", 260.0, 0.0, 67.0, 0.0, cup = 315.0),
        f("rice|white rice|jasmine rice|basmati rice", 365.0, 7.0, 80.0, 0.7, cup = 185.0),
        f("brown rice|wild rice", 370.0, 7.9, 77.0, 2.9, cup = 190.0),
        f("cooked rice|leftover rice", 130.0, 2.7, 28.0, 0.3, cup = 158.0),
        f("pasta|spaghetti|penne|macaroni|elbow macaroni|fettuccine|linguine|rotini|ziti|orzo|lasagna|lasagna noodle|noodle|rice noodle", 371.0, 13.0, 75.0, 1.5, cup = 100.0, units = "box=454,package=454"),
        f("egg noodle", 384.0, 14.0, 71.0, 4.4, cup = 40.0, units = "bag=340,package=340"),
        f("ramen", 436.0, 10.0, 60.0, 17.0, each = 85.0, units = "package=85"),
        f("oat|rolled oat|oatmeal|quick oat", 389.0, 17.0, 66.0, 7.0, cup = 81.0),
        f("quinoa", 368.0, 14.0, 64.0, 6.0, cup = 170.0),
        f("couscous", 376.0, 13.0, 77.0, 0.6, cup = 173.0),
        f("bread|sandwich bread|white bread|wheat bread|sourdough|texas toast|brioche|ciabatta|baguette", 265.0, 9.0, 49.0, 3.2, units = "slice=28,loaf=450"),
        f("bun|hamburger bun|slider bun|roll|dinner roll|hoagie roll|sub roll|english muffin|bagel", 279.0, 9.7, 50.0, 4.3, each = 60.0),
        f("hot dog bun", 279.0, 9.7, 50.0, 4.3, each = 44.0),
        f("tortilla|flour tortilla|wrap", 310.0, 8.0, 52.0, 8.0, each = 45.0),
        f("corn tortilla|taco shell|tostada", 218.0, 5.7, 45.0, 2.9, each = 26.0),
        f("pita|naan|flatbread", 275.0, 9.0, 55.0, 1.2, each = 60.0),
        f("pizza dough|pizza crust", 234.0, 7.0, 47.0, 1.8, each = 450.0),
        f("bread crumb|breadcrumb|panko", 395.0, 13.0, 72.0, 5.3, cup = 108.0),
        f("cracker|saltine", 430.0, 9.0, 72.0, 11.0, each = 3.0, cup = 60.0),
        f("tortilla chip|chip|potato chip", 500.0, 7.0, 62.0, 24.0, cup = 28.0, units = "bag=369"),
        f("black bean|kidney bean|pinto bean|bean|cannellini bean|navy bean|great northern bean|refried bean", 132.0, 8.9, 23.7, 0.5, cup = 172.0, units = "can=240"),
        f("chickpea|garbanzo bean", 164.0, 8.9, 27.0, 2.6, cup = 164.0, units = "can=240"),
        f("baked bean", 94.0, 4.8, 21.0, 0.4, cup = 254.0, units = "can=450"),
        f("lentil", 352.0, 25.0, 63.0, 1.0, cup = 192.0),
        f("cooked lentil", 116.0, 9.0, 20.0, 0.4, cup = 198.0),
        f("peanut butter|almond butter", 588.0, 25.0, 20.0, 50.0, cup = 258.0),
        f("nutella", 539.0, 6.3, 57.0, 31.0, cup = 296.0),
        f("jam|jelly|preserve", 278.0, 0.4, 69.0, 0.1, cup = 320.0),
        f("oil|olive oil|extra virgin olive oil|vegetable oil|canola oil|coconut oil|sesame oil|avocado oil", 884.0, 0.0, 0.0, 100.0, cup = 216.0),
        f("mayonnaise|mayo", 680.0, 1.0, 0.6, 75.0, cup = 220.0),
        f("ranch|dressing|salad dressing|italian dressing", 430.0, 1.0, 6.0, 45.0, cup = 240.0),
        f("ketchup", 101.0, 1.0, 27.0, 0.1, cup = 240.0),
        f("bbq sauce|barbecue sauce", 172.0, 0.8, 41.0, 0.6, cup = 280.0),
        f("teriyaki sauce|hoisin sauce", 90.0, 5.0, 16.0, 0.0, cup = 280.0),
        f("marinara|marinara sauce|pasta sauce|spaghetti sauce|pizza sauce", 50.0, 1.5, 8.0, 1.5, cup = 250.0, units = "jar=680"),
        f("alfredo sauce", 150.0, 3.0, 4.0, 13.0, cup = 250.0, units = "jar=425"),
        f("salsa", 36.0, 1.5, 7.0, 0.2, cup = 260.0, units = "jar=454"),
        f("tomato sauce", 24.0, 1.2, 5.0, 0.2, cup = 245.0, units = "can=425"),
        f("diced tomato|crushed tomato|stewed tomato|fire roasted tomato|whole peeled tomato|tomato puree", 20.0, 1.0, 4.0, 0.1, cup = 240.0, units = "can=411"),
        f("tomato paste", 82.0, 4.3, 19.0, 0.5, cup = 262.0, units = "can=170"),
        f("coconut milk|coconut cream", 197.0, 2.0, 2.8, 21.0, cup = 226.0, units = "can=400"),
        f("evaporated milk", 134.0, 6.8, 10.0, 7.6, cup = 252.0, units = "can=354"),
        f("sweetened condensed milk|condensed milk", 321.0, 7.9, 54.0, 8.7, cup = 306.0, units = "can=397"),
        f("cream of mushroom|cream of chicken|cream of celery", 80.0, 1.5, 7.0, 5.0, cup = 250.0, units = "can=298"),
        f("broth|stock|chicken broth|chicken stock|beef broth|beef stock|vegetable broth|vegetable stock|bone broth", 6.0, 0.6, 0.4, 0.2, cup = 240.0, units = "can=411,carton=946"),
        f("soup", 50.0, 2.5, 7.0, 1.5, cup = 245.0, units = "can=298"),
        f("hummus", 166.0, 8.0, 14.0, 10.0, cup = 246.0),
        f("guacamole", 157.0, 2.0, 8.5, 14.5, cup = 230.0),
        f("chocolate chip|chocolate|baking chocolate", 490.0, 4.2, 63.0, 29.0, cup = 170.0),
        f("cocoa|cocoa powder", 228.0, 20.0, 58.0, 14.0, cup = 86.0),
        f("cornstarch|corn starch", 381.0, 0.3, 91.0, 0.1, cup = 128.0),
        f("cornmeal", 370.0, 7.0, 79.0, 3.6, cup = 157.0),
        f("almond|nut|cashew|peanut|pistachio|pecan|walnut|pine nut", 600.0, 18.0, 20.0, 52.0, cup = 135.0),
        f("raisin|dried cranberry|dried fruit", 300.0, 2.0, 79.0, 0.5, cup = 145.0),
        f("marshmallow", 318.0, 1.8, 81.0, 0.2, cup = 50.0, each = 7.0),
        f("granola", 471.0, 10.0, 64.0, 20.0, cup = 122.0),
        f("cereal", 380.0, 7.0, 84.0, 2.5, cup = 30.0),
        f("olive|black olive|kalamata olive", 115.0, 0.8, 6.0, 10.7, cup = 134.0, each = 4.0),
        f("pesto", 460.0, 5.0, 6.0, 47.0, cup = 250.0),

        // Produce
        f("onion|red onion|yellow onion|white onion|shallot", 40.0, 1.1, 9.3, 0.1, cup = 160.0, each = 110.0),
        f("green onion|scallion", 32.0, 1.8, 7.3, 0.2, cup = 100.0, each = 15.0, units = "bunch=100"),
        f("garlic|garlic clove|minced garlic", 149.0, 6.4, 33.0, 0.5, cup = 136.0, each = 3.0, units = "clove=3,head=40"),
        f("ginger|fresh ginger", 80.0, 1.8, 18.0, 0.8, cup = 96.0),
        f("tomato|roma tomato", 18.0, 0.9, 3.9, 0.2, cup = 180.0, each = 123.0),
        f("cherry tomato|grape tomato", 18.0, 0.9, 3.9, 0.2, cup = 149.0, each = 17.0),
        f("potato", 77.0, 2.0, 17.0, 0.1, cup = 150.0, each = 213.0),
        f("sweet potato|yam", 86.0, 1.6, 20.0, 0.1, cup = 133.0, each = 130.0),
        f("carrot", 41.0, 0.9, 9.6, 0.2, cup = 128.0, each = 61.0),
        f("celery", 16.0, 0.7, 3.0, 0.2, cup = 101.0, each = 40.0, units = "stalk=40,rib=40"),
        f("bell pepper|red pepper|green pepper|yellow pepper|orange pepper|poblano", 26.0, 1.0, 6.0, 0.3, cup = 149.0, each = 120.0),
        f("jalapeno|serrano|habanero", 29.0, 0.9, 6.5, 0.4, each = 14.0),
        f("broccoli", 34.0, 2.8, 7.0, 0.4, cup = 91.0, each = 300.0, units = "head=300,crown=300"),
        f("cauliflower", 25.0, 1.9, 5.0, 0.3, cup = 107.0, each = 575.0, units = "head=575"),
        f("spinach|baby spinach", 23.0, 2.9, 3.6, 0.4, cup = 30.0, units = "bag=283"),
        f("lettuce|romaine|iceberg|butter lettuce", 17.0, 1.2, 3.3, 0.3, cup = 47.0, each = 626.0, units = "head=626"),
        f("mixed green|spring mix|salad mix|salad|arugula|green", 17.0, 1.5, 3.0, 0.2, cup = 30.0, units = "bag=142"),
        f("kale", 35.0, 2.9, 4.4, 1.5, cup = 21.0, units = "bunch=200"),
        f("cabbage|coleslaw mix", 25.0, 1.3, 5.8, 0.1, cup = 89.0, each = 900.0, units = "head=900,bag=400"),
        f("cucumber", 15.0, 0.7, 3.6, 0.1, cup = 104.0, each = 300.0),
        f("zucchini|squash|yellow squash", 17.0, 1.2, 3.1, 0.3, cup = 124.0, each = 196.0),
        f("butternut squash", 45.0, 1.0, 12.0, 0.1, cup = 140.0, each = 1000.0),
        f("mushroom", 22.0, 3.1, 3.3, 0.3, cup = 70.0, each = 18.0, units = "package=227"),
        f("corn|corn on the cob", 86.0, 3.3, 19.0, 1.4, cup = 154.0, each = 90.0, units = "ear=90,can=340"),
        f("green bean", 31.0, 1.8, 7.0, 0.2, cup = 110.0),
        f("pea|snap pea|snow pea", 81.0, 5.4, 14.0, 0.4, cup = 145.0),
        f("asparagus", 20.0, 2.2, 3.9, 0.1, cup = 134.0, each = 16.0, units = "bunch=450"),
        f("eggplant", 25.0, 1.0, 6.0, 0.2, cup = 82.0, each = 460.0),
        f("avocado", 160.0, 2.0, 8.5, 14.7, cup = 150.0, each = 150.0),
        f("lemon", 29.0, 1.1, 9.0, 0.3, each = 84.0),
        f("lime", 30.0, 0.7, 10.5, 0.2, each = 67.0),
        f("apple", 52.0, 0.3, 14.0, 0.2, cup = 125.0, each = 182.0),
        f("banana", 89.0, 1.1, 23.0, 0.3, cup = 150.0, each = 118.0),
        f("orange", 47.0, 0.9, 12.0, 0.1, cup = 180.0, each = 131.0),
        f("strawberry", 32.0, 0.7, 7.7, 0.3, cup = 152.0, each = 12.0),
        f("blueberry", 57.0, 0.7, 14.5, 0.3, cup = 148.0),
        f("raspberry|blackberry|berry|mixed berry", 50.0, 1.2, 12.0, 0.6, cup = 140.0),
        f("grape", 69.0, 0.7, 18.0, 0.2, cup = 151.0),
        f("pineapple", 50.0, 0.5, 13.0, 0.1, cup = 165.0),
        f("mango", 60.0, 0.8, 15.0, 0.4, cup = 165.0, each = 200.0),
        f("peach|nectarine|pear|plum", 45.0, 0.6, 11.5, 0.2, cup = 155.0, each = 150.0),
        f("melon|watermelon|cantaloupe|honeydew", 32.0, 0.7, 8.0, 0.2, cup = 155.0),
        f("orange juice|juice|apple juice", 45.0, 0.7, 10.0, 0.2, cup = 248.0),
        f("wine|red wine|white wine|cooking wine", 83.0, 0.1, 2.6, 0.0, cup = 236.0),
        f("beer", 43.0, 0.5, 3.6, 0.0, cup = 237.0, each = 355.0),

        // Too small to matter
        zero(
            "salt|kosher salt|sea salt|table salt|pepper|black pepper|ground black pepper|white pepper|peppercorn|" +
                "cayenne|cayenne pepper|paprika|smoked paprika|cumin|ground cumin|chili powder|garlic powder|garlic salt|" +
                "onion powder|oregano|dried oregano|basil|dried basil|thyme|dried thyme|parsley|dried parsley|rosemary|" +
                "dried rosemary|dill|dried dill|italian seasoning|taco seasoning|fajita seasoning|cajun seasoning|" +
                "ranch seasoning|seasoning|seasoning salt|seasoned salt|poultry seasoning|cinnamon|ground cinnamon|" +
                "nutmeg|clove|ground clove|allspice|ground ginger|turmeric|curry powder|coriander|red pepper flake|" +
                "crushed red pepper|chili flake|bay leaf|vanilla|vanilla extract|extract|almond extract|baking soda|" +
                "baking powder|yeast|cilantro|mint|chive|sage|herb|fresh herb|water|ice|vinegar|balsamic vinegar|" +
                "red wine vinegar|white wine vinegar|rice vinegar|apple cider vinegar|white vinegar|soy sauce|tamari|" +
                "worcestershire sauce|hot sauce|sriracha|mustard|dijon mustard|yellow mustard|lemon juice|lime juice|" +
                "lemon zest|lime zest|cooking spray|fish sauce|garam masala|old bay|lemon pepper|cream of tartar|" +
                "food coloring|caper|pickle|relish|bouillon|green chile|chipotle|adobo|jalapeno slice",
        ),
    ).flatten()

    private val matcher = KeywordMatcher(foods)

    fun find(name: String): Food? = matcher.match(name)

    private val volumeInCups = mapOf(
        "tsp" to 1.0 / 48, "tbsp" to 1.0 / 16, "cup" to 1.0, "fl oz" to 1.0 / 8,
        "ml" to 1.0 / 236.6, "l" to 1000.0 / 236.6, "pint" to 2.0, "quart" to 4.0, "gallon" to 16.0,
    )
    private val weightInGrams = mapOf("oz" to 28.35, "lb" to 453.6, "g" to 1.0, "kg" to 1000.0)

    /** "(14 oz)" in an amount or name gives the size of one can or package. */
    private fun packageGrams(text: String): Double? {
        val m = Regex("\\(\\s*(\\d+(?:\\.\\d+)?)\\s*-?\\s*(oz|ounce|ounces|g|grams?|lb|lbs?)\\b", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val n = m.groupValues[1].toDouble()
        return when (m.groupValues[2].lowercase().trimEnd('s')) {
            "oz", "ounce" -> n * 28.35
            "lb" -> n * 453.6
            else -> n
        }
    }

    /** Weight in grams of [amount] of [food], or null when it can't be worked out. */
    fun grams(amount: String, name: String, food: Food): Double? {
        val a = amount.trim()
        if (a.isEmpty()) return null
        val pkg = packageGrams(a) ?: packageGrams(name)
        val q = Ingredients.parseQuantity(a)
        if (q == null) {
            // "2 large", "1 (14 oz)": a count of whole items.
            val (count, _) = Ingredients.leadingNumber(a) ?: return null
            val each = pkg ?: food.each ?: return null
            return count * each
        }
        val unit = q.unit
        volumeInCups[unit]?.let { cups -> return food.cup?.let { q.value * cups * it } }
        weightInGrams[unit]?.let { return q.value * it }
        if (unit.isEmpty()) return (pkg ?: food.each)?.let { q.value * it }
        food.units[unit]?.let { return q.value * it }
        return when (unit) {
            "piece", "fillet", "head", "loaf", "ear" -> food.each?.let { q.value * it }
            "dozen" -> food.each?.let { q.value * 12 * it }
            "can" -> q.value * (pkg ?: 400.0)
            "jar" -> q.value * (pkg ?: 450.0)
            "package", "bag", "box" -> pkg?.let { q.value * it }
            "pinch", "dash" -> q.value * 0.3
            else -> null
        }
    }

    /** Adds up every ingredient it can recognise and size. */
    fun estimate(lines: List<IngredientLine>): NutritionEstimate {
        var total = NutritionFacts()
        var counted = 0
        val missing = mutableListOf<String>()
        for (line in lines) {
            if (line.name.isBlank()) continue
            val food = find(line.name)
            if (food == null) {
                missing += line.name.trim()
                continue
            }
            if (food.negligible) {
                counted++
                continue
            }
            val g = grams(line.amount, line.name, food)
            if (g == null) {
                missing += line.name.trim()
                continue
            }
            total += food.per100g * (g / 100.0)
            counted++
        }
        return NutritionEstimate(total, counted, missing)
    }
}
