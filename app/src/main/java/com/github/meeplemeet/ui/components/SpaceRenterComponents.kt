package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterSearchViewModel
import com.github.meeplemeet.ui.sessions.SessionTestTags

/* ----- sizes to keep columns aligned across header and rows ----- */
private val SpaceLabelWidth = 110.dp
private val FieldBoxWidth = 88.dp
private val ColumnsGap = 16.dp

/* ================================================================
 * Required info section (same as shop, adapted to space renters)
 * ================================================================ */
@Composable
fun SpaceRenterRequiredInfoSection(
    spaceRenter: SpaceRenter?,
    spaceName: String,
    onSpaceName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    link: String,
    onLink: (String) -> Unit,
    onPickLocation: (Location) -> Unit,
    viewModel: SpaceRenterSearchViewModel,
    owner: Account
) {
  // Name
  Box(Modifier.testTag(ShopFormTestTags.FIELD_SHOP)) {
    LabeledField(
        label = "Space name",
        placeholder = "Space name",
        value = spaceName,
        onValueChange = onSpaceName)
  }

  // Email
  Box(Modifier.testTag(ShopFormTestTags.FIELD_EMAIL)) {
    LabeledField(
        label = ShopFormUi.Strings.EMAIL_LABEL,
        placeholder = ShopFormUi.Strings.EMAIL_PLACEHOLDER,
        value = email,
        onValueChange = onEmail,
        keyboardType = KeyboardType.Email)
  }
  val showEmailError = email.isNotEmpty() && !isValidEmail(email)
  if (showEmailError) {
    Text(
        "Enter a valid email address.",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }

  // Phone
  Box(Modifier.testTag(ShopFormTestTags.FIELD_PHONE)) {
    LabeledField(
        label = ShopFormUi.Strings.PHONE_LABEL,
        placeholder = ShopFormUi.Strings.PHONE_PLACEHOLDER,
        value = phone,
        onValueChange = onPhone,
        keyboardType = KeyboardType.Phone)
  }

  // Link
  Box(Modifier.testTag(ShopFormTestTags.FIELD_LINK)) {
    LabeledField(
        label = ShopFormUi.Strings.LINK_LABEL,
        placeholder = ShopFormUi.Strings.LINK_PLACEHOLDER,
        value = link,
        onValueChange = onLink,
        keyboardType = KeyboardType.Uri)
  }

  // Address
  Box(Modifier.testTag(ShopFormTestTags.FIELD_ADDRESS)) {
    SpaceRenterLocationSearchBar(
        account = owner,
        spaceRenter = spaceRenter,
        viewModel = viewModel,
        inputFieldTestTag = SessionTestTags.LOCATION_FIELD)
  }
}

/* ================================================================
 * Spaces section header + row (used by CreateSpaceRenterScreen)
 * ================================================================ */
@Composable
fun SpacesHeaderRow(placesTitle: String, priceTitle: String) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(SpaceLabelWidth))

        Text(
            text = placesTitle,
            modifier = Modifier.width(FieldBoxWidth).wrapContentWidth(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.width(ColumnsGap))

        Text(
            text = priceTitle,
            modifier = Modifier.width(FieldBoxWidth).wrapContentWidth(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.weight(1f))
      }
}

@Composable
fun SpaceRow(
    index: Int,
    space: Space,
    onChange: (Space) -> Unit,
    onDelete: () -> Unit,
    borderOpacity: Float = 1f,
    borderColor: Color = MaterialTheme.colorScheme.onSurface
) {
  val outline = borderColor.copy(alpha = borderOpacity)
  val tfColors =
      OutlinedTextFieldDefaults.colors(
          focusedBorderColor = outline,
          unfocusedBorderColor = outline,
          disabledBorderColor = outline,
          errorBorderColor = outline)

  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        "Space N°${index + 1}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.width(SpaceLabelWidth))

    OutlinedTextField(
        value = space.seats.toString(),
        onValueChange = { raw ->
          val clean = raw.filter { it.isDigit() }
          onChange(space.copy(seats = clean.toIntOrNull() ?: 0))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        colors = tfColors,
        modifier = Modifier.width(FieldBoxWidth))

    Spacer(Modifier.width(ColumnsGap))

    OutlinedTextField(
        value = space.costPerHour.toString().removeSuffix(".0"),
        onValueChange = { raw ->
          val cleaned =
              raw.replace(',', '.').filterIndexed { i, c ->
                c.isDigit() || (c == '.' && raw.replace(',', '.').indexOf('.') == i)
              }
          onChange(space.copy(costPerHour = cleaned.toDoubleOrNull() ?: 0.0))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        colors = tfColors,
        modifier = Modifier.width(FieldBoxWidth))

    Spacer(Modifier.weight(1f))

    IconButton(onClick = onDelete) {
      Icon(
          Icons.Filled.Delete,
          contentDescription = "Remove space",
          tint = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
fun SpacesList(
    spaces: List<Space>,
    onChange: (index: Int, updated: Space) -> Unit,
    onDelete: (index: Int) -> Unit,
    placesTitle: String,
    priceTitle: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    rowBorderOpacity: Float = 1f,
    rowBorderColor: Color = MaterialTheme.colorScheme.onSurface,
    maxListHeight: Dp = 600.dp,
    rowSpacing: Dp = 8.dp
) {
  Column(modifier = modifier) {
    SpacesHeaderRow(placesTitle = placesTitle, priceTitle = priceTitle)

    if (spaces.isEmpty()) {
      Text(
          emptyText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      LazyColumn(
          verticalArrangement = Arrangement.spacedBy(rowSpacing),
          contentPadding = PaddingValues(bottom = 16.dp),
          modifier = Modifier.heightIn(max = maxListHeight)) {
            itemsIndexed(spaces) { idx, space ->
              SpaceRow(
                  index = idx,
                  space = space,
                  onChange = { updated -> onChange(idx, updated) },
                  onDelete = { onDelete(idx) },
                  borderOpacity = rowBorderOpacity,
                  borderColor = rowBorderColor)
            }
          }
    }
  }
}

/** Isolated row preview so you can tweak integer/double inputs quickly. */
@Preview(name = "Space Row – inputs", showBackground = true, widthDp = 390)
@Composable
private fun Preview_SpaceRow() {
  MaterialTheme {
    Surface {
      Column(Modifier.padding(16.dp)) {
        SpaceRow(
            index = 0, space = Space(seats = 4, costPerHour = 8.5), onChange = {}, onDelete = {})
        Spacer(Modifier.height(12.dp))
        SpaceRow(
            index = 1, space = Space(seats = 10, costPerHour = 15.0), onChange = {}, onDelete = {})
      }
    }
  }
}
