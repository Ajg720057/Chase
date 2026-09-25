# Meal Planner (Android)

A weekly meal planner with saved recipes and a check-off grocery list. Everything
stays on your phone.

## Install on your phone

1. On your Android phone, open
   **https://github.com/Ajg720057/Chase/releases/tag/meal-planner-latest**
2. Tap **MealPlanner.apk** to download it, then open the download.
3. If Android asks, allow your browser to "install unknown apps", then tap **Install**.

Every push to `meal-planner-app/` rebuilds the APK and replaces the file on that release.
New builds install over the old one and keep your data, because every build is
signed with the same key (`app/mealplanner.keystore`).

## How it works

**Plan tab**
- One card per day of the week, each with **Breakfast**, **Lunch**, **Dinner** and **Snacks**.
- Use the arrows to move between weeks. The 📅 button jumps back to this week.
- Tap an empty slot to add a meal. Tap a meal to see its photo, ingredients, supplies and recipe.
  From there you can **Swap** it for another meal, **Remove** it from that day, or add it to
  another day as well.
- A slot can hold more than one item (for example a main and a side, or several snacks).
- **Leftovers:** if a recipe makes more than you'll eat in one sitting, open the planned meal and
  tap **Plan the leftovers for another day**. That day shows the meal marked "Leftovers", and it
  isn't added to the grocery list again. You can also flip the **Leftovers** switch on any planned
  meal (for example food already in the fridge).
- **Who's eating:** when you each have something different, mark each meal with who it's for
  (tap the names on the meal, or when adding it). The plan and the menu PDF show the name, e.g.
  "Oatmeal · Me" and "Yogurt parfait · Wife". Meals without names are for everyone. Set up the
  names from ⋮ → **People**.
- **Menu PDF** (the PDF button at the top): a printable one-page menu for the week shown, with an
  optional page per meal (photo, ingredients, supplies and recipe). Print it, share it, or save it.
- Each day shows roughly how many calories one serving of each planned meal adds up to.
- ⋮ menu: **Copy last week's meals**, **Clear this week**, **Week starts on Monday**,
  **Weekly reminder**, and backup.

**Adding a meal**
- Type the meal's name. Saved meals that match appear underneath. Tap one and its
  ingredients, supplies, recipe and photo fill in automatically.
- New meals are saved to your library when you tap **Save**, so next time you only
  need to type the name.
- **Take photo** uses the camera; **Choose** picks a picture from your gallery.
- Each ingredient has an amount ("2 cups") and a name ("rice").
  **Paste a list** takes a whole ingredient list copied from a recipe website, one per line,
  and splits the amounts out for you.
- **Supplies** are the things you need that aren't food: foil, skewers, paper plates.
- **Recipe makes … servings** lets the app scale the recipe. On a planned day, open the meal
  and use − / + to say how many you're making that day; the ingredient amounts and the grocery
  list scale to match (for example 4 servings → 8 doubles everything).
- **Nutrition per serving** is estimated from the ingredients using a built-in table of common
  foods (calories, protein, carbs, fat). Anything it doesn't recognise is listed as "Not counted".
  If the recipe website lists nutrition, type those numbers in and they're used instead.
- Editing a saved meal updates it everywhere it's planned.

**Meals tab**
- Every saved meal, searchable by name or ingredient. Use ⋮ on a meal to add it to the plan,
  edit it, or delete it.

**Grocery list tab**
- Tap **Make grocery list** on the Plan tab (or **Build list** here) to list every
  ingredient and supply for the week you're viewing.
- The same ingredient from different meals is combined: 1 cup of rice for one dinner and
  2 cups for another shows as **Rice — 3 cups**, with the meals it's for underneath.
  A meal planned twice counts twice.
- Items are grouped by store section (Produce, Meat & Seafood, Dairy & Eggs, Bakery, Pantry,
  Canned & Jarred, Spices & Baking, Snacks, Frozen, Drinks, Household). If something lands in
  the wrong section, use ⋮ on the item → **Move to aisle**; the app remembers it for next time.
- Tap an item to check it off as it goes in the cart. Checked items move to the bottom
  of their section.
- Add anything else (paper towels, coffee) with the box at the top.
- **Rebuild** after changing the plan. It keeps items you added yourself, and when it's
  the same week, keeps your check marks too.
- **Share** sends the unchecked items as text (to a text message, email or notes app).

**Weekly reminder**
- By default a notification on **Fridays at 1:00 PM** says how much of next week is planned.
  Tapping it opens next week's plan. Change the day and time, or turn it off, from ⋮ →
  **Weekly reminder** on the Plan tab. Allow notifications when Android asks.

**Backup**
- ⋮ on the Plan tab → **Back up to file…** saves everything (meals, photos, plans and
  the grocery list) as one `.zip`. Save it to Google Drive or Downloads.
- **Restore from backup…** on a new phone. Restoring replaces all current data.

## Building locally

This project needs JDK 17 and the Android SDK (API 35). Build it with:

```
cd meal-planner-app
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.
