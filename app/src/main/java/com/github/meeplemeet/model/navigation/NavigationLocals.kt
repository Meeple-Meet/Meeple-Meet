package com.github.meeplemeet.model.navigation

import androidx.compose.runtime.staticCompositionLocalOf

var LocalNavigationVM =
    staticCompositionLocalOf<NavigationViewModel> { error("LocalNavigationVM not provided") }
