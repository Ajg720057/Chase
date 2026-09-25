package com.chase.mealplan.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.data.GroceryItemEntity
import com.chase.mealplan.data.MealEntity
import com.chase.mealplan.data.Person
import com.chase.mealplan.data.PlannedMeal
import com.chase.mealplan.data.ReminderSettings
import com.chase.mealplan.grocery.StoreSection
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.Week
import com.chase.mealplan.pdf.MenuPdf
import com.chase.mealplan.pdf.PdfFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** State shared by the Plan, Meals and Grocery tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val app: MealPlanApp) : ViewModel() {
    private val repo = app.repo
    private val settings = app.settings

    val weekStartsMonday: StateFlow<Boolean> = settings.weekStartsMonday

    private val weekOffset = MutableStateFlow(0L)

    val week: StateFlow<Week> = combine(weekOffset, weekStartsMonday) { offset, monday ->
        Week.containing(LocalDate.now(), monday).plusWeeks(offset)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Week.containing(LocalDate.now(), weekStartsMonday.value))

    val isThisWeek: StateFlow<Boolean> = weekOffset.map { it == 0L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The shown week's plan: day -> slot -> meals. */
    val plan: StateFlow<Map<LocalDate, Map<Slot, List<PlannedMeal>>>> = week
        .flatMapLatest { repo.observeWeek(it) }
        .map { list ->
            list.groupBy { LocalDate.parse(it.entry.date) }
                .mapValues { (_, day) -> day.groupBy { it.entry.slot } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val meals: StateFlow<List<MealEntity>> = repo.allMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grocery: StateFlow<List<GroceryItemEntity>> = repo.groceryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groceryWeek: StateFlow<Week?> = settings.groceryWeek.map { it?.let(::Week) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short confirmations shown in a snackbar. */
    val messages: SharedFlow<String> = _messages

    fun previousWeek() { weekOffset.value -= 1 }
    fun nextWeek() { weekOffset.value += 1 }
    fun thisWeek() { weekOffset.value = 0 }
    fun showNextWeek() { weekOffset.value = 1 }

    val reminder: StateFlow<ReminderSettings> = settings.reminder
    fun setReminder(value: ReminderSettings) = settings.setReminder(value)

    val people: StateFlow<List<Person>> = settings.people
    fun setPeople(value: List<Person>) = settings.setPeople(value)
    fun setEntryEaters(entryId: Long, ids: Set<Int>?) = viewModelScope.launch { repo.setEntryEaters(entryId, ids) }

    fun setEntryServings(entryId: Long, servings: Int?) = viewModelScope.launch { repo.setEntryServings(entryId, servings) }

    fun setSection(item: GroceryItemEntity, section: StoreSection) = viewModelScope.launch { repo.setSection(item, section) }

    fun setWeekStartsMonday(value: Boolean) = settings.setWeekStartsMonday(value)

    fun removeFromPlan(entryId: Long) = app.appScope.launch { repo.removeFromPlan(entryId) }

    fun addToPlan(mealId: Long, date: LocalDate, slot: Slot, leftover: Boolean = false, eaters: String? = null) =
        viewModelScope.launch {
            repo.addToPlan(mealId, date, slot, leftover, eaters)
            val what = if (leftover) "Leftovers added" else "Added"
            _messages.tryEmit("$what to ${date.dayOfWeek.displayName()} ${slot.label.lowercase()}")
        }

    fun setLeftover(entryId: Long, leftover: Boolean) = viewModelScope.launch { repo.setLeftover(entryId, leftover) }

    fun copyPreviousWeek() = viewModelScope.launch {
        val n = repo.copyPreviousWeek(week.value)
        _messages.tryEmit(if (n == 0) "Last week's plan is empty" else "Copied $n meals from last week")
    }

    fun clearWeek() = viewModelScope.launch { repo.clearWeek(week.value) }

    fun deleteMeal(meal: MealEntity) = app.appScope.launch { repo.deleteMeal(meal) }

    fun buildGroceryList(week: Week = this.week.value, onDone: () -> Unit = {}) = viewModelScope.launch {
        val n = repo.buildGroceryList(week)
        _messages.tryEmit(
            if (n == 0) "No ingredients planned for ${week.label} yet"
            else "Grocery list ready: $n items for ${week.label}",
        )
        onDone()
    }

    fun setChecked(item: GroceryItemEntity, checked: Boolean) = viewModelScope.launch { repo.setChecked(item.id, checked) }
    fun addGroceryItem(name: String) = viewModelScope.launch { repo.addGroceryItem(name) }
    fun deleteGroceryItem(item: GroceryItemEntity) = viewModelScope.launch { repo.deleteGroceryItem(item.id) }
    fun clearChecked() = viewModelScope.launch { repo.clearChecked() }
    fun clearGrocery() = viewModelScope.launch { repo.clearGrocery() }

    fun notify(message: String) { _messages.tryEmit(message) }

    /** Builds the menu PDF for the week on screen. Null when nothing is planned that week. */
    suspend fun buildMenuPdf(includeRecipes: Boolean, includePhotos: Boolean): File? = withContext(Dispatchers.IO) {
        val w = week.value
        val planned = repo.weekPlan(w)
        if (planned.isEmpty()) return@withContext null
        val file = PdfFiles.menuFile(app, "Weekly menu ${w.start}.pdf")
        MenuPdf(app.photos).write(w, planned, includeRecipes, includePhotos, file, settings.people.value)
        file
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching { app.backup.export(uri) }
        _messages.tryEmit(if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}")
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching { app.backup.import(uri) }
        _messages.tryEmit(if (result.isSuccess) "Backup restored" else "Restore failed: ${result.exceptionOrNull()?.message}")
    }
}

fun java.time.DayOfWeek.displayName(): String =
    getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
