package com.github.meeplemeet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterSearchViewModel
import com.github.meeplemeet.ui.sessions.SessionTestTags
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
    val spaceLabelWidth = 110.dp
    val fieldBoxWidth = 88.dp
    val columnsGap = 16.dp
    val maxListHeight: Dp = 600.dp
    val rowSpacing: Dp = 8.dp
  }

  object Strings {
    const val spaceNumberPrefix = "Space N°"
    const val spaceNameField = "Space name"
    const val validEmailMsg = "Enter a valid email address."

    const val labelPlaces = "Places"
    const val labelPrices = "Price"
    const val emptySpacesText = "No spaces added yet."
  }

  object Styles {
    const val editingBorderAlpha = 1f
    const val readonlyBorderAlpha = 0.3f
  }
}

/* ================================================================================================
 * Required info section
 * ================================================================================================ */

/**
 * Section with required info fields for a space renter: name, email, phone, link, address.
 *
 * @param spaceRenter The space renter being edited, or null if creating a new one.
 * @param spaceName The current value of the space name field.
 * @param onSpaceName Callback when the space name changes.
 * @param email The current value of the email field.
 * @param onEmail Callback when the email changes.
 * @param phone The current value of the phone field.
 * @param onPhone Callback when the phone changes.
 * @param link The current value of the link field.
 * @param onLink Callback when the link changes.
 * @param onPickLocation Callback when a location is picked.
 * @param viewModel The SpaceRenterSearchViewModel for location searching.
 * @param owner The account of the owner of the space renter.
 */
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
        label = SpaceRenterUi.Strings.spaceNameField,
        placeholder = SpaceRenterUi.Strings.spaceNameField,
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
        SpaceRenterUi.Strings.validEmailMsg,
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
              .padding(bottom = 8.dp)
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
              if (isEditing) SpaceRenterUi.Styles.editingBorderAlpha
              else SpaceRenterUi.Styles.readonlyBorderAlpha)
  val tfColors =
      OutlinedTextFieldDefaults.colors(
          focusedBorderColor = outline,
          unfocusedBorderColor = outline,
          disabledBorderColor = outline,
          errorBorderColor = outline)

  val rowTagBase = SpaceRenterComponentsTestTags.SPACE_ROW_PREFIX + index

  var seatsText by remember(space.seats) { mutableStateOf(max(1, space.seats).toString()) }
  var priceText by
      remember(space.costPerHour) {
        mutableStateOf(
            if (space.costPerHour == 0.0) "0" else space.costPerHour.toString().removeSuffix(".0"))
      }

  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(rowTagBase)) {
    Text(
        "${SpaceRenterUi.Strings.spaceNumberPrefix} ${index + 1}",
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.width(SpaceRenterUi.Dimensions.spaceLabelWidth)
                .testTag(rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_LABEL_SUFFIX))

    OutlinedTextField(
        value = seatsText,
        onValueChange = { raw ->
          val digits = raw.filter(Char::isDigit)
          if (digits.isEmpty()) {
            seatsText = ""
          } else {
            seatsText = digits
            val parsed = digits.toIntOrNull() ?: 1
            val clamped = max(1, parsed)
            if (clamped != space.seats) onChange(space.copy(seats = clamped))
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        colors = tfColors,
        modifier =
            Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
                .onFocusChanged { st ->
                  if (st.isFocused) {
                    if (seatsText == "1" && space.seats == 1) seatsText = ""
                  } else {
                    if (seatsText.isBlank()) {
                      seatsText = "1"
                      if (space.seats != 1) onChange(space.copy(seats = 1))
                    } else {
                      val value = (seatsText.toIntOrNull() ?: 1).coerceAtLeast(1)
                      val normalized = value.toString()
                      if (seatsText != normalized) seatsText = normalized
                      if (space.seats != value) onChange(space.copy(seats = value))
                    }
                  }
                }
                .testTag(rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_SEATS_FIELD_SUFFIX))

    Spacer(Modifier.width(SpaceRenterUi.Dimensions.columnsGap))

    OutlinedTextField(
        value = priceText,
        onValueChange = { raw ->
          val normalized = raw.replace(',', '.')
          var dotSeen = false
          val filtered = buildString {
            for (c in normalized) {
              if (c.isDigit()) append(c)
              else if (c == '.' && !dotSeen) {
                append(c)
                dotSeen = true
              }
            }
          }

          priceText = filtered

          val parsed = filtered.toDoubleOrNull()
          if (parsed != null) {
            val clamped = if (parsed < 0.0) 0.0 else parsed
            if (clamped != space.costPerHour) {
              onChange(space.copy(costPerHour = clamped))
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        colors = tfColors,
        modifier =
            Modifier.width(SpaceRenterUi.Dimensions.fieldBoxWidth)
                .onFocusChanged { st ->
                  if (st.isFocused) {
                    if ((priceText == "0" || priceText == "0.0") && space.costPerHour == 0.0) {
                      priceText = ""
                    }
                  } else {
                    if (priceText.isBlank() || priceText == ".") {
                      priceText = "0"
                      if (space.costPerHour != 0.0) {
                        onChange(space.copy(costPerHour = 0.0))
                      }
                    } else {
                      val normalized = priceText.trimEnd('.')
                      val parsed = normalized.toDoubleOrNull() ?: 0.0
                      val clamped = if (parsed < 0.0) 0.0 else parsed
                      val display = normalized.ifBlank { "0" }
                      if (priceText != display) priceText = display
                      if (space.costPerHour != clamped) {
                        onChange(space.copy(costPerHour = clamped))
                      }
                    }
                  }
                }
                .testTag(rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_PRICE_FIELD_SUFFIX))

    Spacer(Modifier.weight(1f))

    if (isEditing) {
      IconButton(
          onClick = onDelete,
          modifier =
              Modifier.testTag(
                  rowTagBase + SpaceRenterComponentsTestTags.SPACE_ROW_DELETE_SUFFIX)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove space",
                tint = MaterialTheme.colorScheme.error)
          }
    }
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
          contentPadding = PaddingValues(bottom = 16.dp),
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
