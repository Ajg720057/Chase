package com.chase.mealplan.grocery

/** Grocery store areas, in the order the list shows them. */
enum class StoreSection(val label: String) {
    PRODUCE("Produce"),
    MEAT("Meat & Seafood"),
    DAIRY("Dairy & Eggs"),
    BAKERY("Bakery & Bread"),
    PANTRY("Pantry"),
    CANNED("Canned & Jarred"),
    SPICES("Spices & Baking"),
    SNACKS("Snacks"),
    FROZEN("Frozen"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household & Supplies"),
    OTHER("Other"),
}

/**
 * Looks an ingredient name up in a keyword table. Multi-word phrases win over single words
 * ("peanut butter" beats "butter"); otherwise the last known word decides, since that's
 * usually the thing itself ("chicken broth" is broth, "garlic powder" is powder).
 */
class KeywordMatcher<T>(entries: List<Pair<String, T>>) {
    private val phrases = HashMap<String, T>()
    private val singles = HashMap<String, T>()

    init {
        for ((alias, value) in entries) {
            val norm = Ingredients.words(alias).joinToString(" ")
            if (norm.isEmpty()) continue
            if (' ' in norm) phrases.putIfAbsent(norm, value) else singles.putIfAbsent(norm, value)
        }
    }

    fun match(name: String): T? = match(Ingredients.words(name))

    fun match(words: List<String>): T? {
        if (words.isEmpty()) return null
        val text = " " + words.joinToString(" ") + " "
        phrases.entries
            .filter { text.contains(" ${it.key} ") }
            .maxByOrNull { it.key.length }
            ?.let { return it.value }
        for (w in words.asReversed()) singles[w]?.let { return it }
        return null
    }
}

object StoreSections {
    private fun list(section: StoreSection, csv: String) =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it to section }

    private val matcher = KeywordMatcher(
        list(
            StoreSection.PRODUCE,
            """apple, banana, orange, lemon, lime, grapefruit, grape, strawberry, blueberry, raspberry,
            blackberry, berry, cherry, peach, nectarine, pear, plum, mango, pineapple, kiwi, melon, watermelon,
            cantaloupe, honeydew, avocado, tomato, cherry tomato, grape tomato, roma tomato, potato, sweet potato,
            yam, onion, red onion, yellow onion, white onion, green onion, scallion, shallot, garlic, garlic clove,
            ginger, fresh ginger, carrot, celery, bell pepper, red pepper, green pepper, yellow pepper,
            orange pepper, jalapeno, serrano, poblano, habanero, broccoli, cauliflower, spinach, lettuce, romaine,
            iceberg, butter lettuce, arugula, kale, cabbage, brussels sprout, zucchini, squash, butternut squash,
            spaghetti squash, cucumber, mushroom, corn, corn on the cob, green bean, asparagus, eggplant, beet,
            radish, turnip, parsnip, leek, fennel, bok choy, bean sprout, snap pea, snow pea, cilantro, parsley,
            basil, mint, dill, rosemary, thyme, chive, sage, herb, fresh herb, salad, salad mix, mixed green,
            green, spring mix, coleslaw mix, microgreen, fruit, vegetable, veggie, rhubarb, pomegranate, fig,
            lemon juice, lime juice, lemon zest, lime zest, jicama, okra, artichoke, plantain, tomatillo, pumpkin""",
        ) + list(
            StoreSection.MEAT,
            """chicken, chicken breast, chicken thigh, chicken wing, chicken tender, chicken leg, drumstick,
            whole chicken, rotisserie chicken, turkey, ground turkey, turkey breast, beef, ground beef, steak,
            sirloin, ribeye, flank steak, skirt steak, chuck, chuck roast, roast, pot roast, brisket, short rib,
            rib, pork, pork chop, pork loin, pork tenderloin, tenderloin, pork shoulder, pork butt, ground pork,
            bacon, turkey bacon, sausage, italian sausage, breakfast sausage, chorizo, ham, hot dog, bratwurst,
            kielbasa, lamb, veal, meatball, salmon, tuna steak, cod, tilapia, halibut, mahi mahi, trout, catfish,
            fish, white fish, shrimp, prawn, scallop, crab, lobster, clam, mussel, seafood, venison, bison,
            pepperoni, salami, prosciutto, deli meat, lunch meat, deli turkey, sliced turkey, meat, stew meat""",
        ) + list(
            StoreSection.DAIRY,
            """milk, whole milk, skim milk, almond milk, oat milk, soy milk, buttermilk, cream, heavy cream,
            whipping cream, heavy whipping cream, half and half, sour cream, cream cheese, butter, unsalted butter,
            salted butter, margarine, cheese, cheddar, cheddar cheese, mozzarella, parmesan, parmigiano, feta,
            ricotta, cottage cheese, swiss, swiss cheese, provolone, monterey jack, pepper jack, colby, gouda,
            brie, goat cheese, blue cheese, american cheese, string cheese, shredded cheese, mexican cheese,
            yogurt, greek yogurt, egg, egg white, kefir, whipped cream, whipped topping, coffee creamer, creamer,
            tofu, hummus, biscuit dough, crescent roll, refrigerated pie crust, fresh pasta, tortellini, guacamole,
            orange juice""",
        ) + list(
            StoreSection.BAKERY,
            """bread, loaf, baguette, roll, dinner roll, bun, hamburger bun, hot dog bun, slider bun, bagel,
            english muffin, muffin, croissant, pita, naan, tortilla, flour tortilla, corn tortilla, wrap,
            flatbread, sourdough, brioche, ciabatta, texas toast, pizza crust, pizza dough, cake, donut,
            sandwich bread, white bread, wheat bread, hoagie roll, sub roll, garlic bread""",
        ) + list(
            StoreSection.PANTRY,
            """rice, brown rice, white rice, jasmine rice, basmati rice, wild rice, pasta, spaghetti, penne,
            macaroni, elbow macaroni, fettuccine, linguine, lasagna, lasagna noodle, rotini, ziti, orzo, noodle,
            egg noodle, ramen, rice noodle, couscous, quinoa, oat, oatmeal, rolled oat, cereal, granola,
            bread crumb, breadcrumb, panko, peanut butter, almond butter, apple butter, nutella, jam, jelly,
            preserve, honey, maple syrup, syrup, pancake syrup, oil, olive oil, extra virgin olive oil,
            vegetable oil, canola oil, coconut oil, sesame oil, avocado oil, cooking spray, vinegar,
            balsamic vinegar, red wine vinegar, white wine vinegar, rice vinegar, apple cider vinegar,
            white vinegar, soy sauce, tamari, teriyaki sauce, worcestershire sauce, hot sauce, sriracha,
            bbq sauce, barbecue sauce, ketchup, mustard, dijon mustard, yellow mustard, mayonnaise, mayo, ranch,
            dressing, salad dressing, italian dressing, relish, sauce, taco shell, tostada, lentil, nut, almond,
            walnut, pecan, cashew, peanut, pistachio, pine nut, raisin, dried cranberry, dried fruit, seed,
            chia seed, flax seed, sesame seed, sunflower seed, pumpkin seed, stuffing, instant potato,
            mac and cheese, rice mix, pancake mix, waffle mix, cornbread mix, biscuit mix, bisquick, fish sauce,
            oyster sauce, hoisin sauce, buffalo sauce, steak sauce, coconut flake, shredded coconut""",
        ) + list(
            StoreSection.CANNED,
            """canned, broth, stock, bouillon, soup, chicken broth, chicken stock, beef broth, beef stock,
            vegetable broth, vegetable stock, bone broth, tomato sauce, tomato paste, diced tomato, crushed tomato,
            stewed tomato, fire roasted tomato, tomato puree, whole peeled tomato, marinara, marinara sauce,
            pasta sauce, spaghetti sauce, pizza sauce, alfredo sauce, enchilada sauce, salsa, black bean,
            kidney bean, pinto bean, cannellini bean, navy bean, great northern bean, garbanzo bean, chickpea,
            refried bean, baked bean, bean, green chile, diced green chile, coconut milk, coconut cream,
            evaporated milk, condensed milk, sweetened condensed milk, cream of mushroom, cream of chicken,
            cream of celery, tuna, canned tuna, canned chicken, olive, black olive, kalamata olive, pickle,
            artichoke heart, roasted red pepper, water chestnut, bamboo shoot, pumpkin puree, canned pumpkin,
            applesauce, fruit cocktail, mandarin orange, curry paste, chipotle, chipotle pepper, adobo, caper,
            sun dried tomato, pesto, anchovy, sauerkraut, jarred garlic, minced garlic""",
        ) + list(
            StoreSection.SPICES,
            """salt, kosher salt, sea salt, table salt, pepper, black pepper, ground black pepper, white pepper,
            peppercorn, cayenne, cayenne pepper, paprika, smoked paprika, cumin, ground cumin, chili powder,
            garlic powder, garlic salt, onion powder, oregano, dried oregano, dried basil, dried thyme,
            dried parsley, dried rosemary, dried dill, dried herb, italian seasoning, taco seasoning,
            fajita seasoning, cajun seasoning, ranch seasoning, seasoning, seasoning salt, seasoned salt,
            everything bagel seasoning, poultry seasoning, cinnamon, ground cinnamon, nutmeg, clove,
            ground clove, allspice, ground ginger, turmeric, curry powder, curry, coriander, red pepper flake,
            crushed red pepper, chili flake, bay leaf, vanilla, vanilla extract, extract, almond extract,
            baking soda, baking powder, yeast, flour, all purpose flour, bread flour, whole wheat flour,
            almond flour, self rising flour, cornmeal, cornstarch, corn starch, sugar, white sugar, brown sugar,
            powdered sugar, confectioner sugar, granulated sugar, cane sugar, cocoa, cocoa powder, chocolate chip,
            baking chocolate, sprinkle, food coloring, gelatin, pudding mix, cake mix, brownie mix, frosting,
            icing, shortening, molasses, cream of tartar, gravy mix, powder, spice, herbs de provence,
            garam masala, chili seasoning, old bay, lemon pepper, dry mustard, mustard powder, sage leaf""",
        ) + list(
            StoreSection.SNACKS,
            """chip, tortilla chip, potato chip, corn chip, pita chip, pretzel, popcorn, cracker, graham cracker,
            saltine, cookie, granola bar, protein bar, trail mix, candy, chocolate, chocolate bar, gummy,
            fruit snack, marshmallow, rice cake, pork rind, goldfish, beef jerky, jerky, snack""",
        ) + list(
            StoreSection.FROZEN,
            """frozen, ice cream, popsicle, frozen pizza, waffle, frozen waffle, tater tot, french fry, fry, ice,
            puff pastry, phyllo, frozen vegetable, pizza roll, fish stick, chicken nugget, nugget, frozen fruit,
            frozen berry, frozen pea, frozen corn, sherbet, frozen yogurt, pie crust""",
        ) + list(
            StoreSection.DRINKS,
            """water, sparkling water, seltzer, soda, juice, apple juice, cranberry juice, grape juice, lemonade,
            coffee, ground coffee, coffee bean, k cup, tea, tea bag, iced tea, beer, wine, red wine, white wine,
            cooking wine, liquor, vodka, rum, tequila, whiskey, bourbon, sports drink, gatorade, energy drink,
            kombucha, sparkling cider, coconut water, drink, club soda, tonic, ginger ale""",
        ) + list(
            StoreSection.HOUSEHOLD,
            """paper towel, toilet paper, napkin, paper plate, plate, paper cup, plastic cup, solo cup, foil,
            aluminum foil, tin foil, plastic wrap, cling wrap, parchment paper, parchment, wax paper, zip bag,
            ziploc, ziploc bag, sandwich bag, freezer bag, storage bag, trash bag, garbage bag, dish soap,
            detergent, dishwasher detergent, dishwasher pod, laundry detergent, sponge, soap, hand soap, shampoo,
            conditioner, toothpaste, toothbrush, deodorant, skewer, toothpick, charcoal, lighter fluid, battery,
            light bulb, cleaner, bleach, wipe, disinfecting wipe, tissue, diaper, baby wipe, candle, straw,
            utensil, plastic fork, plastic spoon, plastic utensil, container, tupperware, lunch bag, muffin liner,
            cupcake liner, cooking twine, twine, propane, pan, baking dish, foil pan, cake pan, bag""",
        ),
    )

    /** Best guess at where [name] lives in the store. [isSupply] marks non-food items from a recipe. */
    fun classify(name: String, isSupply: Boolean = false): StoreSection {
        val words = Ingredients.words(name)
        if ("frozen" in words) return StoreSection.FROZEN
        if ("canned" in words || "can" in words) return StoreSection.CANNED
        return matcher.match(words) ?: if (isSupply) StoreSection.HOUSEHOLD else StoreSection.OTHER
    }
}
