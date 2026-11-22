package com.thomas.packing

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout.LayoutParams

/**
 * Packing APK v2: country-keyed packing lists in four categories
 * (Essentials / Clothing / Accessories / Other), card layout like the
 * original packing.html: light sky background, white rounded cards,
 * orange accent progress. Storage: app-private JSON.
 */
class MainActivity : Activity() {

    companion object {
        val CATEGORIES = listOf("Essentials", "Clothing", "Accessories", "Other")
    }

    private lateinit var rootScroll: ScrollView
    private lateinit var listArea: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressLabel: TextView
    private var countries = LinkedHashMap<String, Map<String, MutableList<Item>>>()
    private var current: String? = null

    data class Item(var text: String, var qty: Int, var done: Boolean)

    private fun seed(text: String, qty: Int = 1) = Item(text, qty, false)

    private fun template(): Map<String, MutableList<Item>> = mapOf(
        "Essentials" to mutableListOf(
            seed("Passport + copies"),
            seed("Wallet"),
            seed("Credit cards"),
            seed("Currencies — real cash"),
            seed("Phone + charger"),
            seed("Power bank"),
            seed("Charging cables + plug adapter"),
            seed("5G router"),
            seed("eSIM phone"),
            seed("Garmin Instinct 2S + charger"),
            seed("Spare glasses (regular)"),
            seed("Spare glasses (computer)"),
            seed("Notebook + pencil"),
            seed("Toiletries bag"),
            seed("Sunscreen"),
            seed("Mosquito repellent"),
            seed("First aid kit"),
            seed("Coffee powder"),
            seed("Oat flakes"),
            seed("USB OTG stick")
        ),
        "Clothing" to mutableListOf(
            seed("T-shirts", 4),
            seed("Underwear", 6),
            seed("Socks", 4),
            seed("Trousers", 2),
            seed("Shorts"),
            seed("Shirt"),
            seed("Swimwear", 2),
            seed("Rain jacket"),
            seed("Fleece / warm layer"),
            seed("Walking shoes"),
            seed("Sandals"),
            seed("Towel"),
            seed("Hat / cap")
        ),
        "Accessories" to mutableListOf(
            seed("Anker NC headphones"),
            seed("FiiO player + IEMs"),
            seed("Sunglasses"),
            seed("Day pack"),
            seed("Water bottle"),
            seed("Luggage lock"),
            seed("Eye mask + earplugs"),
            seed("Laundry bag")
        ),
        "Other" to mutableListOf()
    )

    private fun emptyCats(): Map<String, MutableList<Item>> =
        CATEGORIES.associateWith { mutableListOf() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        buildUi()
        if (current == null && countries.isNotEmpty()) current = countries.keys.first()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(10).toFloat()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFCFE6F5.toInt())
        }
        setContentView(root)

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
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(menuBtn)
        header.addView(titleView)
        root.addView(header)

        // progress
        val pwrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        progressLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF6B7A88.toInt())
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFF5A623.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(10))
        }
        pwrap.addView(progressLabel)
        pwrap.addView(progress)
        root.addView(pwrap)

        // scrollable list
        listArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        rootScroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        rootScroll.addView(listArea)
        root.addView(rootScroll)

        // footer buttons
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFE8F3FB.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }
        fun footBtn(label: String, weight: Float, action: () -> Unit) =
            Button(this).apply {
                text = label
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
                setOnClickListener { action() }
            }
        footer.addView(footBtn("+ Item", 1f) { addItemDialog() })
        footer.addView(footBtn("Uncheck all", 1f) {
            countries[current]?.values?.forEach { l -> l.forEach { it.done = false } }
            save(); render()
        })
        footer.addView(footBtn("New from template", 1.3f) { newFromTemplate() })
        root.addView(footer)
    }

    // ---------- rendering ----------

    private fun render() {
        titleView.text = current ?: "No country"
        listArea.removeAllViews()
        val cats = countries[current] ?: return

        var total = 0; var done = 0
        cats.values.forEach { l -> total += l.size; done += l.count { it.done } }
        progress.max = if (total == 0) 1 else total
        progress.progress = done
        progressLabel.text = "$done / $total packed"

        for (cat in CATEGORIES) {
            val items = cats[cat] ?: mutableListOf()
            val head = TextView(this).apply {
                text = cat.uppercase()
                textSize = 12f
                setTextColor(0xFF6B7A88.toInt())
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(6))
            }
            listArea.addView(head)

            if (items.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "—"
                    setTextColor(0xFF9FB2C2.toInt())
                    setPadding(dp(6), 0, 0, 0)
                }
                listArea.addView(empty)
            }
            items.forEachIndexed { idx, item -> listArea.addView(itemRow(items, idx, item)) }
        }
    }

    private fun itemRow(items: MutableList<Item>, idx: Int, item: Item): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg()
            setPadding(dp(14), dp(11), dp(8), dp(11))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        val check = CheckBox(this).apply {
            isChecked = item.done
            setOnCheckedChangeListener { _, v -> item.done = v; save(); updateProgressOnly() }
        }
        val label = TextView(this).apply {
            text = item.text + if (item.qty > 1) "  ×${item.qty}" else ""
            textSize = 15f
            setTextColor(if (item.done) 0xFF999999.toInt() else 0xFF25313D.toInt())
            if (item.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { item.done = !item.done; save(); render() }
        }
        row.addView(check)
        row.addView(label)
        if (item.qty > 0) {
            val minus = Button(this).apply {
                text = "−"; textSize = 13f
                setOnClickListener { if (item.qty > 1) { item.qty--; save(); render() } }
            }
            val plus = Button(this).apply {
                text = "+"; textSize = 13f
                setOnClickListener { item.qty++; save(); render() }
            }
            for (b in listOf(minus, plus)) {
                b.layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
                b.setPadding(0, 0, 0, 0)
                b.minimumWidth = 0; b.minimumHeight = 0
                row.addView(b)
            }
        }
        val del = Button(this).apply {
            text = "✕"; textSize = 13f
            setTextColor(0xFF6B7A88.toInt())
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Delete \"${item.text}\"?")
                    .setPositiveButton("Delete") { _, _ -> items.removeAt(idx); save(); render() }
                    .setNegativeButton("Cancel", null).show()
            }
        }
        del.layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
        del.setPadding(0, 0, 0, 0)
        del.minimumWidth = 0; del.minimumHeight = 0
        row.addView(del)
        return row
    }

    private fun updateProgressOnly() {
        val cats = countries[current] ?: return
        var total = 0; var done = 0
        cats.values.forEach { l -> total += l.size; done += l.count { it.done } }
        progress.max = if (total == 0) 1 else total
        progress.progress = done
        progressLabel.text = "$done / $total packed"
    }

    // ---------- country menu ----------

    private fun showCountryMenu() {
        val names = countries.keys.toList()
        val labels = names + "+ New country…" + "+ New from template…"
        AlertDialog.Builder(this)
            .setTitle("Countries")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < names.size -> { current = names[which]; render() }
                    which == names.size -> newCountryDialog()
                    else -> newFromTemplate()
                }
            }
            .setNeutralButton("Rename current") { _, _ -> renameDialog() }
            .setNegativeButton("Delete current") { _, _ -> deleteCurrentDialog() }
            .show()
    }

    private fun createCountry(name: String, fromTemplate: Boolean) {
        val n = name.trim()
        if (n.isEmpty() || n in countries) return
        countries[n] = if (fromTemplate) template() else emptyCats()
        current = n; save(); render()
    }

    private fun nameInput(initial: String = "", block: (EditText) -> Unit): AlertDialog.Builder {
        val input = EditText(this).apply {
            setText(initial)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        block(input)
        return AlertDialog.Builder(this).setView(input)
    }

    private fun newCountryDialog() {
        val input = EditText(this).apply {
            hint = "Country name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("New country")
            .setView(input)
            .setPositiveButton("From template") { _, _ -> createCountry(input.text.toString(), true) }
            .setNeutralButton("Empty") { _, _ -> createCountry(input.text.toString(), false) }
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
            .setPositiveButton("Create") { _, _ -> createCountry(input.text.toString(), true) }
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
                    current = n; save(); render()
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
                save(); render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- items ----------

    private fun addItemDialog() {
        val c = current ?: return
        val input = EditText(this).apply {
            hint = "Item name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, CATEGORIES)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }
        box.addView(input)
        box.addView(TextView(this).apply { text = "Category"; setPadding(0, dp(12), 0, dp(4)) })
        box.addView(spinner)
        AlertDialog.Builder(this)
            .setTitle("Add item to \"$c\"")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    val cat = CATEGORIES[spinner.selectedItemPosition]
                    val cats = countries[c]!!.toMutableMap()
                    val list = cats[cat]!!.toMutableList()
                    list.add(Item(t, 1, false))
                    cats[cat] = list
                    countries[c] = cats
                    save(); render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- persistence ----------

    private fun save() {
        val arr = org.json.JSONArray()
        for ((name, cats) in countries) {
            val c = org.json.JSONObject()
            c.put("name", name)
            val co = org.json.JSONObject()
            for ((cat, items) in cats) {
                val ia = org.json.JSONArray()
                for (it in items) {
                    val o = org.json.JSONObject()
                    o.put("text", it.text); o.put("qty", it.qty); o.put("done", it.done)
                    ia.put(o)
                }
                co.put(cat, ia)
            }
            c.put("cats", co)
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
                val cats = if (c.has("cats")) {
                    val co = c.getJSONObject("cats")
                    val m = emptyCats().toMutableMap()
                    for (cat in CATEGORIES) {
                        if (co.has(cat)) {
                            val ia = co.getJSONArray(cat)
                            val l = mutableListOf<Item>()
                            for (j in 0 until ia.length()) {
                                val o = ia.getJSONObject(j)
                                l.add(Item(o.getString("text"), o.optInt("qty", 1), o.optBoolean("done", false)))
                            }
                            m[cat] = l
                        }
                    }
                    m.toMap()
                } else {
                    // v1 format: flat "items" array -> put in Essentials
                    val ia = c.getJSONArray("items")
                    val l = mutableListOf<Item>()
                    for (j in 0 until ia.length()) {
                        val o = ia.getJSONObject(j)
                        l.add(Item(o.getString("text"), o.optInt("qty", 1), o.optBoolean("done", false)))
                    }
                    mapOf("Essentials" to l, "Clothing" to mutableListOf<Item>(), "Accessories" to mutableListOf(), "Other" to mutableListOf())
                }
                countries[c.getString("name")] = cats
            }
            current = countries.keys.firstOrNull()
        } catch (e: Exception) {
            // first run: nothing stored
        }
    }
}
