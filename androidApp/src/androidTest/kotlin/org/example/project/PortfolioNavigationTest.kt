package org.example.project

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.example.project.shared.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun categorySelectionShowsTheCorrespondingPortfolioMeasures() {
        text(R.string.collections).performScrollTo().performClick()
        text(R.string.due_today).assertExists()
        text(R.string.due_week).assertExists()
        text(R.string.cash_flow).performClick()
        text(R.string.total_released).assertExists()
        text(R.string.total_collected).assertExists()
        text(R.string.accounts).performClick()
        text(R.string.total_clients).assertExists()
        text(R.string.active_loans).assertExists()
    }

    @Test
    fun balanceShortcutOpensTheLoanList() {
        text(R.string.view_loans).performScrollTo().performClick()
        text(R.string.new_loan).assertIsDisplayed()
    }

    @Test
    fun clientShortcutFocusesTheSearchField() {
        text(R.string.find_client).performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun overviewScrollsBehindDockAndFinalReportActionCanClearIt() {
        val heading = compose.activity.getString(R.string.portfolio_overview)
        val page = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(heading)))
        val viewport = page.fetchSemanticsNode().boundsInRoot
        val tab = text(R.string.nav_overview).fetchSemanticsNode().boundsInRoot
        assertTrue("The overview should extend behind the navigation", viewport.bottom >= tab.bottom)

        val scroll = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scroll.maxValue()) }
        val report = text(R.string.generate_report_pdf).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("The report button should scroll above the navigation", report.boundsInRoot.bottom <= tab.top)
    }

    private fun text(label: Int) = compose.onNodeWithText(compose.activity.getString(label))
}
