package com.example.pathreport

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TripScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectStartEndAndInspectTrip() {
        val p = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
        val e = Elder("e", "가상 어르신")
        val state = mutableStateOf(CareData(listOf(p), listOf(e), listOf(PlaceElder("p", "e"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TripScreen(state.value, false) { state.value = it(state.value) } } } }
        compose.onNodeWithText("등원 운행 시작").performClick()
        compose.onNodeWithText("선택한 대상으로 시작").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("선택한 대상으로 시작").performClick()
        compose.onNodeWithText("등원 운행 진행 중").assertExists()
        compose.onNodeWithText("운행 종료").performScrollTo().performClick()
        compose.onNodeWithText("종료하고 저장").performClick()
        compose.onNodeWithText("등원 운행 시작").assertExists()
        compose.runOnIdle {
            assertEquals(1, state.value.trips.size)
            assertNotNull(state.value.trips.single().endedAt)
            assertEquals("p", state.value.trips.single().targets.single().place.id)
        }
    }
    @Test fun missingCoordinatesCannotBeSelected() {
        val p = StopPlace("p", "가상 장소", "가상 주소")
        val e = Elder("e", "가상 어르신")
        val data = CareData(listOf(p), listOf(e), listOf(PlaceElder("p", "e")))
        compose.setContent { MaterialTheme { TripScreen(data, false) {} } }
        compose.onNodeWithText("등원 운행 시작").performClick()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("선택한 대상으로 시작").assertIsNotEnabled()
    }
    @Test fun departureButtonStartsDepartureTrip() {
        val p = StopPlace("p", "가상 장소", "가상 주소", 37.5, 127.0)
        val e = Elder("e", "가상 어르신")
        val state = mutableStateOf(CareData(listOf(p), listOf(e), listOf(PlaceElder("p", "e"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TripScreen(state.value, false) { state.value = it(state.value) } } } }
        compose.onNodeWithText("하원 운행 시작").performClick()
        compose.onNodeWithText("하원 운행 대상·장소 선택").assertExists()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("선택한 대상으로 시작").performClick()
        compose.onNodeWithText("하원 운행 진행 중").assertExists()
        compose.runOnIdle { assertEquals(TripDirection.FROM_CENTER, state.value.trips.single().direction) }
    }
    @Test fun nameTapSelectsElderWithoutShowingSinglePlaceOrAddress() {
        val p = StopPlace("p", "비표시 장소", "비표시 상세 주소", 37.5, 127.0)
        val e = Elder("e", "이름 선택 확인")
        val state = mutableStateOf(CareData(listOf(p), listOf(e), listOf(PlaceElder("p", "e"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TripScreen(state.value, false) { state.value = it(state.value) } } } }
        compose.onNodeWithText("등원 운행 시작").performClick()
        compose.onNodeWithText("1. 이름 선택 확인").performClick()
        compose.onNodeWithText("비표시 상세 주소").assertDoesNotExist()
        compose.onNodeWithText("비표시 장소").assertDoesNotExist()
        compose.onNodeWithText("선택한 대상으로 시작").performClick()
        compose.runOnIdle { assertEquals("p", state.value.trips.single().targets.single().place.id) }
    }
    @Test fun multiplePlacesCanBeSelectedByNameWithoutAnAddress() {
        val a = StopPlace("a", "가상 장소 가", "숨긴 주소 가", 37.5, 127.0)
        val b = StopPlace("b", "가상 장소 나", "숨긴 주소 나", 37.6, 127.1)
        val e = Elder("e", "장소 선택 확인")
        val state = mutableStateOf(CareData(listOf(a, b), listOf(e), listOf(PlaceElder("a", "e"), PlaceElder("b", "e"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TripScreen(state.value, false) { state.value = it(state.value) } } } }
        compose.onNodeWithText("하원 운행 시작").performClick()
        compose.onNodeWithText("1. 장소 선택 확인").performClick()
        compose.onNodeWithText("가상 장소 나").performClick()
        compose.onNodeWithText("숨긴 주소 가").assertDoesNotExist()
        compose.onNodeWithText("숨긴 주소 나").assertDoesNotExist()
        compose.onNodeWithText("선택한 대상으로 시작").performClick()
        compose.runOnIdle { assertEquals("b", state.value.trips.single().targets.single().place.id) }
    }
}
