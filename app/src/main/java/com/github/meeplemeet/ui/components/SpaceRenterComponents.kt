// This file was initially done by hand and
// then improved and refactored using ChatGPT-5 Extend Thinking
// Docstrings were generated using copilot from Android studio
package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterSearchViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.theme.Dimensions
import kotlin.math.max

/* ================================================================================================
 * Test tags
 * ================================================================================================ */
object SpaceRenterComponentsTestTags {
  // Space list
  const val SPACES_LIST = "spaces_list"
  const val SPACES_EMPTY_TEXT = "spaces_empty_text"

  // Header
  const val SPACES_HEADER = "spaces_header"
  const val SPACES_HEADER_PLACES = "spaces_header_places"
  const val SPACES_HEADER_PRICE = "spaces_header_price"

  // Space rows
  const val SPACE_ROW_PREFIX = "space_row_"
  const val SPACE_ROW_LABEL_SUFFIX = "_label"
  const val SPACE_ROW_SEATS_FIELD_SUFFIX = "_seats"
  const val SPACE_ROW_PRICE_FIELD_SUFFIX = "_price"
  const val SPACE_ROW_DELETE_SUFFIX = "_delete"
}

/* ================================================================================================
 * UI defaults
 * ================================================================================================ */
private object SpaceRenterUi {
  object Dimensions {
    val spaceLabelWidth = com.github.meeplemeet.ui.theme.Dimensions.ComponentWidth.spaceLabelWidth
    val fieldBoxWidth = com.github.meeplemeet.ui.theme.Dimensions.ComponentWidth.fieldBoxWidth
    val columnsGap = com.github.meeplemeet.ui.theme.Dimensions.Spacing.extraLarge
    val maxListHeight: Dp = com.github.meeplemeet.ui.theme.Dimensions.ContainerSize.maxListHeight
    val rowSpacing: Dp = com.github.meeplemeet.ui.theme.Dimensions.Spacing.medium
  }

  object Strings {
    const val spaceNumberPrefix = "Space N°"
    const val spaceNameField = "Space name"
    const val validEmailMsg = "Enter a valid email address."

    const val labelPlaces = "Places"
    const val labelPrices = "Price"
    const val emptySpacesText = "No spaces added yet."
    const val CONTACT_INFO = "Contact Info"
  }
}

/* ================================================================================================
 * Helpers
 * ================================================================================================ */

/**
 * Sanitizes input for a positive integer field by removing non-digit characters.
 *
 * @param raw The raw input string.
 * @return A string containing only digit characters.
 */
private fun sanitizePositiveIntInput(raw: String): String = raw.filter(Char::isDigit)

/**
 * Sanitizes input for a decimal number field by allowing only digits and a single decimal point.
 *
 * @param raw The raw input string.
 * @return A string formatted as a decimal number.
 */
private fun sanitizeDecimalInput(raw: String): String {
  val normalized = raw.replace(',', '.')
  var dotSeen = false
  val out = StringBuilder()
  for (c in normalized) {
    if (c.isDigit()) out.append(c)
    else if (c == '.' && !dotSeen) {
      out.append('.')
      dotSeen = true
    }
  }
  return out.toString()
}

/**
 * Handles focus change for an integer input field, normalizing the text and committing changes.
 *
 * @param isFocused Whether the field is currently focused.
 * @param text The current text in the field.
 * @param current The current integer value represented by the field.
 * @param min The minimum allowed value (default is 1).
 * @param onText Callback to update the text in the field.
 * @param onCommit Callback to commit the integer value.
 */
private fun handleIntFocusChange(
    isFocused: Boolean,
    text: String,
    current: Int,
    min: Int = 1,
    onText: (String) -> Unit,
    onCommit: (Int) -> Unit,
) {
  if (isFocused) {
    if (text == min.toString() && current == min) onText("")
    return
  }
  if (text.isBlank()) {
    onText(min.toString())
    if (current != min) onCommit(min)
  } else {
    val value = (text.toIntOrNull() ?: min).coerceAtLeast(min)
    val normalized = value.toString()
    if (text != normalized) onText(normalized)
    if (current != value) onCommit(value)
  }
}

/**
 * Handles focus change for a decimal input field, normalizing the text and committing changes.
 *
 * @param isFocused Whether the field is currently focused.
 * @param text The current text in the field.
 * @param current The current decimal value represented by the field.
 * @param onText Callback to update the text in the field.
 * @param onCommit Callback to commit the decimal value.
 */
private fun handleDecimalFocusChange(
    isFocused: Boolean,
    text: String,
    current: Double,
    onText: (String) -> Unit,
    onCommit: (Double) -> Unit,
) {
  if (isFocused) {
    if ((text == "0" || text == "0.0") && current == 0.0) onText("")
    return
  }
  if (text.isBlank() || text == ".") {
    onText("0")
    if (current != 0.0) onCommit(0.0)
  } else {
    val normalized = text.trimEnd('.')
    val parsed = normalized.toDoubleOrNull() ?: 0.0
    val clamped = if (parsed < 0.0) 0.0 else parsed
    val display = normalized.ifBlank { "0" }
    if (text != display) onText(display)
    if (current != clamped) onCommit(clamped)
  }
}

/* ================================================================================================
 * Required info section
 * ================================================================================================ */

/**
 * Section with required info fields for a space renter: name, email, phone, link, address.
 *
 * @param spaceRenter The space renter being edited, or null if creating a new one.
 * @param onSpaceName Callback when the space name changes.
 * @param onEmail Callback when the email changes.
 * @param onPhone Callback when the phone changes.
 * @param onLink Callback when the website/link changes.
 * @param onPickLocation Callback when a location is picked.
 * @param viewModel The SpaceRenterSearchViewModel for location searching.
 * @param owner The account of the owner of the space renter.
 */
@Composable
fun SpaceRenterRequiredInfoSection(
    spaceRenter: SpaceRenter?,
    online: Boolean,
    onSpaceName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPhone: (String) -> Unit,
    onLink: (String) -> Unit,
    onPickLocation: (Location) -> Unit,
    viewModel: SpaceRenterSearchViewModel,
    owner: Account
) {
  // Read current values from the model
  val nameValue = spaceRenter?.name.orEmpty()
  val emailValue = spaceRenter?.email.orEmpty()
  val phoneValue = spaceRenter?.phone.orEmpty()
  val linkValue = spaceRenter?.website.orEmpty()

  // Name
  Box(Modifier.testTag(ShopFormTestTags.FIELD_SHOP)) {
    LabeledField(
        label = SpaceRenterUi.Strings.spaceNameField,
        placeholder = SpaceRenterUi.Strings.spaceNameField,
        value = nameValue,
        onValueChange = onSpaceName)
  }
  // Address
  Box(Modifier.testTag(ShopFormTestTags.FIELD_ADDRESS)) {
    SpaceRenterLocationSearchBar(
        account = owner,
        spaceRenter = spaceRenter,
        viewModel = viewModel,
        enabled = online,
        inputFieldTestTag = SessionTestTags.LOCATION_FIELD,
        dropdownItemTestTag = SessionTestTags.LOCATION_FIELD_ITEM)
  }
  Text(
      text = SpaceRenterUi.Strings.CONTACT_INFO,
      style = MaterialTheme.typography.titleMedium,
  )

  // Email
  Box(Modifier.testTag(ShopFormTestTags.FIELD_EMAIL)) {
    LabeledField(
        leadingIcon = {
          Icon(imageVector = Icons.Default.MailOutline, contentDescription = "phone icon")
        },
        label = ShopFormUi.Strings.EMAIL_LABEL,
        placeholder = ShopFormUi.Strings.EMAIL_PLACEHOLDER,
        value = emailValue,
        onValueChange = onEmail,
        keyboardType = KeyboardType.Email)
  }
  val showEmailError = emailValue.isNotEmpty() && !isValidEmail(emailValue)
  if (showEmailError) {
    Text(
        SpaceRenterUi.Strings.validEmailMsg,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
  }
  Row {
    // Phone
    Box(Modifier.weight(1f).testTag(ShopFormTestTags.FIELD_PHONE)) {
      LabeledField(
          leadingIcon = {
            Icon(imageVector = Icons.Default.Phone, contentDescription = "phone icon")
          },
          label = ShopFormUi.Strings.PHONE_LABEL,
          placeholder = ShopFormUi.Strings.PHONE_PLACEHOLDER,
          value = phoneValue,
          onValueChange = onPhone,
          keyboardType = KeyboardType.Phone,
          scrollToStartOnFocusLost = true)
    }

    Spacer(Modifier.width(Dimensions.Spacing.medium))

    // Link / Website
    Box(Modifier.weight(1f).testTag(ShopFormTestTags.FIELD_LINK)) {
      LabeledField(
          leadingIcon = {
            Icon(imageVector = Icons.Default.AddLink, contentDescription = "link icon")
          },
          label = ShopFormUi.Strings.LINK_LABEL,
          placeholder = ShopFormUi.Strings.LINK_PLACEHOLDER,
          value = linkValue,
          onValueChange = onLink,
          keyboardType = KeyboardType.Uri,
          scrollToStartOnFocusLost = true)
    }
  }
}

/* ================================================================================================
 * Spaces section
 * ================================================================================================ */

/**
 * Header row for the spaces list, showing titles for places and price columns.
 *
 * @param placesTitle The title for the places column.
 * @param priceTitle The title for the price column.
 */
@Composable
fun SpacesHeaderRow(placesTitle: String, priceTitle: String) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(bottom = com.github.meeplemeet.ui.theme.Dimensions.Spacing.medium)
              .testTag(SpaceRenterComponentsTestTags.SPACES_HEADER),
      verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(SpaceRenterUi.Dimensions.spaceLabelWidth))

        Text(
            text = placesTitle,
            modifier =
                Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .testTag(SpaceRenterComponentsTestTags.SPACES_HEADER_PLACES),
            style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.width(SpaceRenterUi.Dimensions.columnsGap))

        Text(
            text = priceTitle,
            modifier =
                Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .testTag(SpaceRenterComponentsTestTags.SPACES_HEADER_PRICE),
            style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.weight(1f))
      }
}

/**
 * A row representing a single space, with fields for number of seats and cost per hour.
 *
 * @param index The index of the space in the list.
 * @param space The space data.
 * @param onChange Callback when the space is changed.
 * @param onDelete Callback when the space is deleted.
 * @param isEditing Whether the row is in editing mode (shows delete button).
 */
@Composable
fun SpaceRow(
    index: Int,
    space: Space,
    onChange: (Space) -> Unit,
    onDelete: () -> Unit,
    isEditing: Boolean = false
) {
  // Outline opacity: 1f when editing, 0.3f otherwise
  val outline =
      MaterialTheme.colorScheme.onSurface.copy(
          alpha =
              if (isEditing) Dimensions.Alpha.editingBorder else Dimensions.Alpha.readonlyBorder)

  val tfColors =
      OutlinedTextFieldDefaults.colors(
          focusedBorderColor = outline,
          unfocusedBorderColor = outline,
          disabledBorderColor = outline,
          errorBorderColor = outline)

  val rowTagBase = SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + index

  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(rowTagBase)) {
    SpaceNumberLabel(index = index, rowTagBase = rowTagBase)

    SeatsField(
        seats = space.seats,
        tfColors = tfColors,
        isEditing = isEditing,
        fieldTag = rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_SEATS_FIELD_SUFFIX,
        onCommit = { v -> if (v != space.seats) onChange(space.copy(seats = v)) })

    Spacer(Modifier.width(SpaceRenterUi.Dimensions.columnsGap))

    PriceField(
        price = space.costPerHour,
        tfColors = tfColors,
        isEditing = isEditing,
        fieldTag = rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_PRICE_FIELD_SUFFIX,
        onCommit = { v -> if (v != space.costPerHour) onChange(space.copy(costPerHour = v)) })

    Spacer(Modifier.weight(1f))

    if (isEditing) {
      DeleteSpaceButton(
          onDelete = onDelete,
          testTag = rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_DELETE_SUFFIX)
    }
  }
}

/**
 * Label displaying the space number.
 *
 * @param index The index of the space.
 * @param rowTagBase The base test tag for the row.
 */
@Composable
private fun SpaceNumberLabel(index: Int, rowTagBase: String) {
  Text(
      "${SpaceRenterUi.Strings.spaceNumberPrefix} ${index + 1}",
      style = MaterialTheme.typography.titleMedium,
      modifier =
          Modifier.width(SpaceRenterUi.Dimensions.spaceLabelWidth)
              .testTag(rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_LABEL_SUFFIX))
}

/**
 * Input field for the number of seats in a space.
 *
 * @param seats The current number of seats.
 * @param tfColors The colors to use for the text field.
 * @param fieldTag The test tag for the text field.
 * @param onCommit Callback when the number of seats is committed.
 */
@Composable
private fun SeatsField(
    seats: Int,
    tfColors: TextFieldColors,
    isEditing: Boolean,
    fieldTag: String,
    onCommit: (Int) -> Unit
) {
  var seatsText by remember(seats) { mutableStateOf(max(1, seats).toString()) }

  FocusableInputField(
      value = seatsText,
      enabled = isEditing,
      onValueChange = { raw ->
        val digits = sanitizePositiveIntInput(raw)
        seatsText = digits
        if (digits.isNotEmpty()) {
          val parsed = digits.toIntOrNull() ?: 1
          val clamped = max(1, parsed)
          if (clamped != seats) onCommit(clamped)
        }
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
      colors = tfColors.copy(disabledTextColor = MaterialTheme.colorScheme.onSurface),
      modifier =
          Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
              .onFocusChanged { st ->
                handleIntFocusChange(
                    isFocused = st.isFocused,
                    text = seatsText,
                    current = seats,
                    min = 1,
                    onText = { seatsText = it },
                    onCommit = onCommit)
              }
              .testTag(fieldTag))
}

/**
 * Input field for the price per hour of a space.
 *
 * @param price The current price value.
 * @param tfColors The colors to use for the text field.
 * @param fieldTag The test tag for the text field.
 * @param onCommit Callback when the price is committed.
 */
@Composable
private fun PriceField(
    price: Double,
    tfColors: TextFieldColors,
    fieldTag: String,
    isEditing: Boolean,
    onCommit: (Double) -> Unit
) {
  var priceText by
      remember(price) {
        mutableStateOf(if (price == 0.0) "0" else price.toString().removeSuffix(".0"))
      }

  FocusableInputField(
      value = priceText,
      enabled = isEditing,
      onValueChange = { raw ->
        val filtered = sanitizeDecimalInput(raw)
        priceText = filtered
        filtered.toDoubleOrNull()?.let { parsed ->
          val clamped = if (parsed < 0.0) 0.0 else parsed
          if (clamped != price) onCommit(clamped)
        }
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
      colors = tfColors.copy(disabledTextColor = MaterialTheme.colorScheme.onSurface),
      modifier =
          Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
              .onFocusChanged { st ->
                handleDecimalFocusChange(
                    isFocused = st.isFocused,
                    text = priceText,
                    current = price,
                    onText = { priceText = it },
                    onCommit = onCommit)
              }
              .testTag(fieldTag))
}

/**
 * Delete button for a space row.
 *
 * @param onDelete Callback when the delete button is clicked.
 * @param testTag The test tag for the delete button.
 */
@Composable
private fun DeleteSpaceButton(onDelete: () -> Unit, testTag: String) {
  IconButton(onClick = onDelete, modifier = Modifier.testTag(testTag)) {
    Icon(
        Icons.Filled.Delete,
        contentDescription = "Remove space",
        tint = MaterialTheme.colorScheme.error)
  }
}

/**
 * A list of spaces with the ability to edit or delete each space.
 *
 * @param spaces The list of spaces to display.
 * @param onChange Callback when a space is changed, providing the index and updated space.
 * @param onDelete Callback when a space is deleted, providing the index.
 * @param modifier Modifier for the list container.
 * @param isEditing Whether the list is in editing mode (shows delete buttons).
 */
@Composable
fun SpacesList(
    spaces: List<Space>,
    onChange: (index: Int, updated: Space) -> Unit,
    onDelete: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = true
) {
  Column(modifier = modifier.testTag(SpaceRenterComponentsTestTags.SPACES_LIST)) {
    if (spaces.isEmpty()) {
      Text(
          SpaceRenterUi.Strings.emptySpacesText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag(SpaceRenterComponentsTestTags.SPACES_EMPTY_TEXT))
    } else {
      SpacesHeaderRow(
          placesTitle = SpaceRenterUi.Strings.labelPlaces,
          priceTitle = SpaceRenterUi.Strings.labelPrices)
      LazyColumn(
          verticalArrangement = Arrangement.spacedBy(SpaceRenterUi.Dimensions.rowSpacing),
          contentPadding =
              PaddingValues(bottom = com.github.meeplemeet.ui.theme.Dimensions.Spacing.extraLarge),
          modifier = Modifier.heightIn(max = SpaceRenterUi.Dimensions.maxListHeight)) {
            itemsIndexed(spaces) { idx, space ->
              SpaceRow(
                  index = idx,
                  space = space,
                  onChange = { updated -> onChange(idx, updated) },
                  onDelete = { onDelete(idx) },
                  isEditing = isEditing)
            }
          }
    }
  }
}
