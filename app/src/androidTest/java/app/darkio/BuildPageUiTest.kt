package app.darkio

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.AiAppCardData
import app.darkio.dashboard.AiPageData
import app.darkio.dashboard.AiShortcut
import app.darkio.dashboard.BuildPage
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * F15, F17, F20: Side 3 ("byg") viser:
 * - Øverst: "ai i dag" og dagens tip (med langt tryk til kopiering)
 * - Nederst: AI-kort for installerede AI-apps (Claude, ChatGPT, Gemini)
 * - Knapper med mindst 44 dp højde og TalkBack-labels som "Ny chat i Claude"
 * - Ingen genvejsrække på side 3
 */
class BuildPageUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val claude = AppEntry("com.anthropic.claude/MainActivity#0", "Claude")
    private val chatgpt = AppEntry("com.openai.chatgpt/MainActivity#0", "ChatGPT")
    private val gemini = AppEntry("com.google.android.apps.bard/MainActivity#0", "Gemini")

    @Test
    fun side3InHomeScreenShowsAiTodayAndTipAndNoShortcutsRow() {
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(claude), emptySet()).copy(favoriteSlots = mapOf(claude.id to 0)),
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
            )
        }
        // Skift til side 3 ("byg")
        rule.onNodeWithTag("pageDot2").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("aiTodayCard").assertIsDisplayed()
        rule.onNodeWithTag("buildTipCard").assertIsDisplayed()
        rule.onNodeWithTag("buildTodayTip", useUnmergedTree = true).assertIsDisplayed()

        // Side 3 har ingen genvejsrække
        rule.onNodeWithTag("shortcuts").assertDoesNotExist()
    }

    @Test
    fun aiCardsDisplayContentFixedOrderAndButtonsWithTalkBack() {
        val s1 = AiShortcut("s1", "com.anthropic.claude", "Ny chat", isDynamic = false, rank = 0)
        val s2 = AiShortcut("s2", "com.anthropic.claude", "Søg", isDynamic = false, rank = 1)
        val g1 = AiShortcut("g1", "com.google.android.apps.bard", "Prompt", isDynamic = false, rank = 0)

        val pageData = AiPageData(
            cards = listOf(
                AiAppCardData(claude, "Projekt Alpha", listOf(s1, s2), 1700000000000L, 25 * 60 * 1000L),
                AiAppCardData(chatgpt, null, emptyList(), 1700000000000L, 10 * 60 * 1000L),
                AiAppCardData(gemini, "Brainstorm", listOf(g1), 0L, 0L),
            ),
            totalTodayMillis = 35 * 60 * 1000L,
            hasUsageAccess = true,
        )

        var launchedApp: AppEntry? = null
        var startedShortcut: Pair<String, String>? = null

        rule.setContent {
            BuildPage(
                apps = listOf(gemini, chatgpt, claude), // Uordnet input
                onLaunch = { launchedApp = it },
                loadData = { pageData },
                onStartShortcut = { pkg, id -> startedShortcut = pkg to id },
            )
        }

        // Tjek rækkefølge og indhold for Claude
        rule.onNodeWithTag("aiAppName_${claude.id}", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Claude").assertIsDisplayed()
        rule.onNodeWithText("Projekt Alpha").assertIsDisplayed()

        // Tjek TalkBack label på Claude genvej
        rule.onNodeWithTag("aiShortcut_s1").assertContentDescriptionEquals("Ny chat i Claude")
        rule.onNodeWithTag("aiShortcut_s2").assertContentDescriptionEquals("Søg i Claude")

        // Tjek ChatGPT uden samtaler
        rule.onNodeWithTag("aiAppName_${chatgpt.id}", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ai_no_conversations)).assertIsDisplayed()

        // Tjek Gemini
        rule.onNodeWithTag("aiAppName_${gemini.id}", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Gemini").assertIsDisplayed()
        rule.onNodeWithText("Brainstorm").assertIsDisplayed()

        // Tjek tryk på app-række åbner app
        rule.onNodeWithTag("aiAppRow_${claude.id}").performClick()
        assertEquals(claude, launchedApp)

        // Tjek tryk på genvej åbner genvej
        rule.onNodeWithTag("aiShortcut_s1").performClick()
        assertEquals("com.anthropic.claude" to "s1", startedShortcut)
    }

    @Test
    fun aiCardsAreInBottomHalfAndButtonsAreAtLeast44dp() {
        val s1 = AiShortcut("s1", "com.anthropic.claude", "Ny chat", isDynamic = false, rank = 0)
        val s2 = AiShortcut("s2", "com.anthropic.claude", "Søg", isDynamic = false, rank = 1)

        val pageData = AiPageData(
            cards = listOf(
                AiAppCardData(claude, "Seneste chat", listOf(s1, s2), 1700000000000L, 1000L),
            ),
            totalTodayMillis = 1000L,
            hasUsageAccess = true,
        )

        rule.setContent {
            BuildPage(
                apps = listOf(claude),
                loadData = { pageData },
            )
        }

        val rootBounds = rule.onRoot().getUnclippedBoundsInRoot()
        val middle = rootBounds.height / 2
        val cardBounds = rule.onNodeWithTag("aiCard_${claude.id}").getUnclippedBoundsInRoot()

        // AI-kort ligger i nederste halvdel (F15)
        assertTrue(
            "AI-kort (${cardBounds.top}) skal ligge i nederste halvdel (midte: $middle)",
            cardBounds.top >= middle
        )

        // Knapper mindst 44 dp (DarkIOSize.TouchMin)
        val buttonBounds = rule.onNodeWithTag("aiShortcut_s1").getUnclippedBoundsInRoot()
        assertTrue(
            "Knap højde (${buttonBounds.height.value}) skal være mindst 44 dp",
            buttonBounds.height.value >= 43.9f
        )
    }

    @Test
    fun tipCardCopiesTipOnLongClick() {
        rule.setContent {
            BuildPage(
                apps = emptyList(),
            )
        }

        rule.onNodeWithTag("buildTipCard").performTouchInput { longClick() }
        rule.waitForIdle()

        // Tjek at udklipsholder modtog tippet
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            assertNotNull("Udklipsholder må ikke være tom", clip)
            assertTrue("Udklipsholder skal indeholde mindst ét element", clip!!.itemCount > 0)
            val copiedText = clip.getItemAt(0).text.toString()
            assertTrue("Kopieret tekst må ikke være tom", copiedText.isNotBlank())
        }

        // Tjek bekræftelsestekst
        rule.onNodeWithText("// ${context.getString(R.string.tip_copied)}_").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenNoAiAppsInstalled() {
        rule.setContent {
            BuildPage(
                apps = emptyList(),
            )
        }

        rule.onNodeWithTag("aiEmptyState").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ai_empty)).assertIsDisplayed()
    }
}
