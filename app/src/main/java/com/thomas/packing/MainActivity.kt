package com.thomas.packing

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AppCompatActivity

/**
 * Packing APK: country-keyed packing lists with a shared template.
 * Storage: app-private JSON (survives restarts). Countries can be added,
 * renamed, deleted; "New from template" seeds the default kit.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var listArea: LinearLayout
    private lateinit var titleView: TextView
    private var countries = LinkedHashMap<String, MutableList<Item>>()
    private var current: String? = null

    data class Item(var text: String, var qty: Int, var done: Boolean)

    // ---- template: your base kit, edit freely in-app ----
    private val template = mutableListOf(
        Item("Passport + copies", 1, false),
        Item("Phone + charger", 1, false),
        Item("Power bank", 1, false),
        Item("5G router", 1, false),
        Item("eSIM phone", 1, false),
        Item("Spare glasses (regular)", 1, false),
        Item("Spare glasses (computer)", 1, false),
        Item("Notebook + pencil", 1, false),
        Item("Solar panel", 1, false),
        Item("Coffee powder", 1, false),
        Item("Oat flakes", 1, false),
        Item("T-shirts", 4, false),
        Item("Underwear", 6, false),
        Item("Socks", 4, false),
        Item("Trousers", 2, false),
        Item("Shorts", 1, false),
        Item("Toiletries bag", 1, false),
        Item("Sunscreen", 1, false),
        Item("Mosquito repellent", 1, false),
        Item("First aid kit", 1, false),
        Item("Sandals", 1, false),
        Item("Walking shoes", 1, false),
        Item("Swimwear", 1, false),
        Item("Towel", 1, false),
        Item("Rain jacket", 1, false),
        Item("Fleece / warm layer", 1, false),
        Item("USB OTG stick", 1, false)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F0E8.toInt())
        }
        setContentView(root)
        buildUi()
        if (current == null && countries.isNotEmpty()) current = countries.keys.first()
        showCountry()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        // header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1A2A4A.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val menuBtn = Button(this).apply {
            text = "☰"
            setOnClickListener { showCountryMenu() }
        }
        titleView = TextView(this).apply {
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(menuBtn)
        header.addView(titleView)
        root.addView(header)

        // scrollable list
        listArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        scroll.addView(listArea)
        root.addView(scroll)

        // footer buttons
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }
        fun footBtn(label: String, weight: Float, action: () -> Unit) =
            Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
                setOnClickListener { action() }
            }
        footer.addView(footBtn("+ Add item", 1f) { addItemDialog(current!!, null) })
        footer.addView(footBtn("Reset ticks", 1f) {
            countries[current]?.forEach { it.done = false }
            save(); showCountry()
        })
        footer.addView(footBtn("New from template", 1.3f) { newFromTemplate() })
        root.addView(footer)
    }

    // ---------- rendering ----------

    private fun showCountry() {
        titleView.text = current ?: "No country"
        listArea.removeAllViews()
        val items = countries[current] ?: return
        if (items.isEmpty()) {
            val hint = TextView(this).apply {
                text = "Empty list. Use + Add item, or delete this country and use New from template."
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            listArea.addView(hint)
        }
        items.forEachIndexed { idx, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            val check = CheckBox(this).apply {
                isChecked = item.done
                setOnCheckedChangeListener { _, v -> item.done = v; save() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val label = TextView(this).apply {
                text = item.text + if (item.qty > 1) "  ×${item.qty}" else ""
                textSize = 16f
                if (item.done) {
                    paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    setTextColor(0xFF999999.toInt())
                } else {
                    setTypeface(null, Typeface.NORMAL)
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8), 0, 0, 0)
                setOnClickListener {
                    item.done = !item.done; save(); showCountry()
                }
            }
            val minus = Button(this).apply {
                text = "−"; textSize = 14f
                setOnClickListener { if (item.qty > 1) { item.qty--; save(); showCountry() } }
            }
            val plus = Button(this).apply {
                text = "+"; textSize = 14f
                setOnClickListener { item.qty++; save(); showCountry() }
            }
            val del = Button(this).apply {
                text = "✕"; textSize = 14f
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Delete \"${item.text}\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            items.removeAt(idx); save(); showCountry()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            for (b in listOf(minus, plus, del)) {
                b.layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
                b.setPadding(0, 0, 0, 0)
                b.minimumWidth = 0
                b.minimumHeight = 0
            }
            row.addView(check)
            row.addView(label)
            row.addView(minus)
            row.addView(plus)
            row.addView(del)
            listArea.addView(row)
        }
    }

    // ---------- country menu ----------

    private fun showCountryMenu() {
        val names = countries.keys.toList()
        val labels = names.map { it } + "+ New country…" + "+ New from template…"
        AlertDialog.Builder(this)
            .setTitle("Countries")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < names.size -> { current = names[which]; showCountry() }
                    which == names.size -> newCountryDialog(null)
                    else -> newFromTemplate()
                }
            }
            .setNeutralButton("Rename current") { _, _ -> renameDialog() }
            .setNegativeButton("Delete current") { _, _ -> deleteCurrentDialog() }
            .show()
    }

    private fun newCountryDialog(name: String?) {
        val input = EditText(this).apply {
            hint = "Country name"
            setText(name ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("New country")
            .setView(input)
            .setPositiveButton("Create empty") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n !in countries) { countries[n] = mutableListOf(); current = n; save(); showCountry() }
            }
            .setNeutralButton("Start from template") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n !in countries) {
                    countries[n] = template.map { Item(it.text, it.qty, false) }.toMutableList()
                    current = n; save(); showCountry()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newFromTemplate() {
        val input = EditText(this).apply {
            hint = "Country name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("New packing list from template")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n !in countries) {
                    countries[n] = template.map { Item(it.text, it.qty, false) }.toMutableList()
                    current = n; save(); showCountry()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameDialog() {
        val c = current ?: return
        val input = EditText(this).apply { setText(c) }
        AlertDialog.Builder(this)
            .setTitle("Rename \"$c\"")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty() && n !in countries) {
                    countries[n] = countries.remove(c)!!
                    current = n; save(); showCountry()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentDialog() {
        val c = current ?: return
        AlertDialog.Builder(this)
            .setMessage("Delete country \"$c\" and its list?")
            .setPositiveButton("Delete") { _, _ ->
                countries.remove(c)
                current = countries.keys.firstOrNull()
                save(); showCountry()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- items ----------

    private fun addItemDialog(country: String, after: (() -> Unit)?) {
        val input = EditText(this).apply {
            hint = "Item name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Add item to \"$country\"")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    countries[country]?.add(Item(t, 1, false))
                    save(); showCountry()
                }
                after?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- persistence ----------

    private fun save() {
        val arr = org.json.JSONArray()
        for ((name, items) in countries) {
            val c = org.json.JSONObject()
            c.put("name", name)
            val ia = org.json.JSONArray()
            for (it in items) {
                val o = org.json.JSONObject()
                o.put("text", it.text); o.put("qty", it.qty); o.put("done", it.done)
                ia.put(o)
            }
            c.put("items", ia)
            arr.put(c)
        }
        openFileOutput("packing.json", MODE_PRIVATE).use { it.write(arr.toString().toByteArray()) }
    }

    private fun load() {
        try {
            val raw = openFileInput("packing.json").bufferedReader().readText()
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val items = mutableListOf<Item>()
                val ia = c.getJSONArray("items")
                for (j in 0 until ia.length()) {
                    val o = ia.getJSONObject(j)
                    items.add(Item(o.getString("text"), o.optInt("qty", 1), o.optBoolean("done", false)))
                }
                countries[c.getString("name")] = items
            }
            current = countries.keys.firstOrNull()
        } catch (e: Exception) {
            // first run: nothing stored
        }
    }
}
