package dev.octoshrimpy.quik.feature.organization

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.repository.OrganizationRepository
import javax.inject.Inject

/** Offline management screen for the three local-first organization features. */
class OrganizationActivity : QkThemedActivity() {
    @Inject lateinit var organizationRepo: OrganizationRepository

    private lateinit var content: LinearLayout
    private var section = Section.CATEGORIES

    private enum class Section { CATEGORIES, TAGS, VERIFIED }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setTitle(R.string.organization_title)
        showBackButton(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 16)
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tabs.addView(tabButton(R.string.organization_categories, Section.CATEGORIES))
        tabs.addView(tabButton(R.string.organization_tags, Section.TAGS))
        tabs.addView(tabButton(R.string.organization_verified_senders, Section.VERIFIED))
        root.addView(tabs)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun tabButton(label: Int, target: Section) = Button(this).apply {
        setText(label)
        setOnClickListener { section = target; render() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun render() {
        content.removeAllViews()
        when (section) {
            Section.CATEGORIES -> renderCategories()
            Section.TAGS -> renderTags()
            Section.VERIFIED -> renderVerifiedSenders()
        }
    }

    private fun renderCategories() {
        addButton(R.string.organization_add) { prompt(getString(R.string.organization_categories)) { name ->
            organizationRepo.addCategory(name)
            render()
        } }
        organizationRepo.getCategories().forEach { category ->
            row(category, if (category == "General") null else {
                organizationRepo.deleteCategory(category)
                render()
            })
        }
    }

    private fun renderTags() {
        text(getString(R.string.organization_tags))
        organizationRepo.getTags().forEach { tag -> row("#$tag", null) }
        if (organizationRepo.getTags().isEmpty()) text(getString(R.string.organization_empty))
    }

    private fun renderVerifiedSenders() {
        addButton(R.string.organization_add) { showVerifiedSenderDialog() }
        val senders = organizationRepo.getVerifiedSenders()
        if (senders.isEmpty()) text(getString(R.string.organization_empty))
        senders.forEach { sender ->
            row("✓ ${sender.displayName}\n${sender.address}\n${sender.description}", {
                organizationRepo.removeVerifiedSender(sender.address).subscribe()
                render()
            })
        }
    }

    private fun addButton(label: Int, action: () -> Unit) {
        Button(this).apply {
            setText(label)
            setOnClickListener { action() }
        }.also { content.addView(it) }
    }

    private fun text(value: String) {
        TextView(this).apply {
            text = value
            setPadding(12, 18, 12, 18)
        }.also { content.addView(it) }
    }

    private fun row(value: String, delete: (() -> Unit)?) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        TextView(this).apply {
            text = value
            setPadding(12, 12, 12, 12)
        }.also { line.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        delete?.let {
            Button(this).apply {
                setText(android.R.string.delete)
                setOnClickListener { it() }
            }.also(line::addView)
        }
        content.addView(line)
    }

    private fun prompt(title: String, callback: (String) -> Unit) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.button_save) { _, _ -> callback(input.text.toString()) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun showVerifiedSenderDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val address = EditText(this).apply { hint = "Sender number or mask" }
        val name = EditText(this).apply { hint = "Official name" }
        val description = EditText(this).apply { hint = "Description" }
        form.addView(address); form.addView(name); form.addView(description)
        AlertDialog.Builder(this)
            .setTitle(R.string.organization_verified_senders)
            .setView(form)
            .setPositiveButton(R.string.button_save) { _, _ ->
                organizationRepo.saveVerifiedSender(
                    OrganizationRepository.VerifiedSenderData(
                        address = address.text.toString(),
                        displayName = name.text.toString(),
                        description = description.text.toString(),
                    )
                ).subscribe { runOnUiThread(::render) }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }
}
