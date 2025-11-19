package com.github.meeplemeet.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.ui.discussions.UITestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarousel(
    modifier: Modifier = Modifier,
    maxNumberOfImages: Int = 10,
    photoCollectionUrl: List<String>,
    onAdd: suspend (String, Int) -> Unit = { _, _ -> },
    onRemove: (String) -> Unit = {}
) {
  // --- Setup ---
  val canAddMoreImages = photoCollectionUrl.size < maxNumberOfImages
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current

  // --- Pager state ---
  val pagerState =
      rememberPagerState(
          pageCount = {
            if (canAddMoreImages) photoCollectionUrl.size + 1 else photoCollectionUrl.size
          })

  // --- Image source selection state ---
  var showImageSourceMenu by remember { mutableStateOf(false) }

  // --- Camera launcher ---
  val cameraLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
          coroutineScope.launch {
            val path = ImageFileUtils.saveBitmapToCache(context, bitmap)
            onAdd(path, pagerState.currentPage)
          }
        }
      }
  // --- Image upload handler ---
  val uploadImage: suspend (String) -> Unit = { path ->
    try {
      onAdd(path, pagerState.currentPage)
    } catch (e: Exception) {
      // Handle error silently or show a snackbar
    }
  }

  // --- Camera permission launcher ---
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          cameraLauncher.launch(null)
        }
      }

  // --- Gallery launcher ---
  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
          coroutineScope
          coroutineScope.launch {
            val path = ImageFileUtils.cacheUriToFile(context, uri)
            uploadImage(path)
          }
        }
      }

  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        colors =
            CardColors(
                containerColor = AppColors.secondary,
                contentColor = AppColors.textIcons,
                disabledContentColor = AppColors.textIconsFade,
                disabledContainerColor = AppColors.negative),
        shape = MaterialTheme.shapes.medium) {
          HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if ((photoCollectionUrl.isEmpty() || page == photoCollectionUrl.size) &&
                canAddMoreImages) {
              Box(
                  modifier =
                      Modifier.fillMaxSize().padding(Dimensions.Padding.giant).clickable {
                        if (canAddMoreImages) showImageSourceMenu = true
                      },
                  contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Add Photo",
                        modifier =
                            Modifier.size(
                                Dimensions.IconSize.massive.times(
                                    Dimensions.Multipliers.quadruple)),
                        tint = Color.Gray.copy(alpha = Dimensions.Alpha.dialogIconTranslucent))
                  }
            } else {
              Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photoCollectionUrl[page],
                    contentDescription = "Discussion Profile Picture",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { showImageSourceMenu = true })
                if (page < photoCollectionUrl.size) {
                  Box(
                      modifier =
                          Modifier.align(Alignment.TopEnd)
                              .padding(8.dp)
                              .size(28.dp)
                              .clip(CircleShape)
                              .background(Color.Red)
                              .clickable { onRemove(photoCollectionUrl[page]) },
                      contentAlignment = Alignment.Center) {
                        Text(
                            text = "-",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium)
                      }
                }
              }
            }
          }
        }

    Spacer(Modifier.height(12.dp))

    // --- Dots Indicator
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
          if (photoCollectionUrl.isNotEmpty()) {
            repeat(photoCollectionUrl.size + 1) { index ->
              val selected = pagerState.currentPage == index

              Box(
                  modifier =
                      Modifier.padding(4.dp)
                          .size(if (selected) 10.dp else 8.dp)
                          .clip(CircleShape)
                          .background(
                              if (selected) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceVariant))
            }
          }
        }
    if (showImageSourceMenu) {
      GalleryDialog(
          pageNumber = pagerState.currentPage,
          galleryPictureUrl = photoCollectionUrl,
          onDismiss = { showImageSourceMenu = false },
          onTakePhoto = {
            showImageSourceMenu = false
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED) {
              cameraLauncher.launch(null)
            } else {
              cameraPermissionLauncher.launch(permission)
            }
          },
          onChooseFromGallery = {
            showImageSourceMenu = false
            galleryLauncher.launch("image/*")
          })
    }
  }
}

@Composable
fun PhotoDialogTopBar(
    modifier: Modifier = Modifier,
    text: String = "Add Photo",
    onDismiss: () -> Unit
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .background(
                  Brush.verticalGradient(
                      listOf(
                          Color.Black.copy(alpha = Dimensions.Alpha.dialogOverlayDark),
                          Color.Black.copy(alpha = Dimensions.Alpha.dialogOverlayTransparent))))) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.Padding.extraLarge,
                        vertical = Dimensions.Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
              IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White)
              }

              Text(
                  text = text,
                  color = Color.White,
                  style = MaterialTheme.typography.titleMedium,
                  modifier =
                      Modifier.weight(Dimensions.Weight.full)
                          .padding(horizontal = Dimensions.Spacing.large)
                          .testTag(UITestTags.PROFILE_PICTURE_DIALOG_TITLE),
                  textAlign = TextAlign.Center)

              Spacer(modifier = Modifier.width(Dimensions.IconSize.extraLarge))
            }
      }
}

@Composable
fun PhotoDialogBottomBar(
    modifier: Modifier = Modifier,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .background(
                  Brush.verticalGradient(
                      listOf(Color.Black.copy(alpha = 0.0f), Color.Black.copy(alpha = 0.9f))))) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.Padding.extraLarge,
                        vertical = Dimensions.Spacing.extraLarge),
            horizontalArrangement =
                Arrangement.spacedBy(Dimensions.Spacing.large, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
              Button(
                  onClick = onTakePhoto,
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = Color.White.copy(alpha = 0.2f),
                          contentColor = Color.White),
                  modifier =
                      Modifier.weight(1f).testTag(UITestTags.PROFILE_PICTURE_CAMERA_OPTION)) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Take Photo",
                        modifier = Modifier.size(Dimensions.IconSize.large))
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                    Text("Camera", style = MaterialTheme.typography.bodyLarge)
                  }

              Button(
                  onClick = onChooseFromGallery,
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = Color.White.copy(alpha = 0.2f),
                          contentColor = Color.White),
                  modifier =
                      Modifier.weight(1f).testTag(UITestTags.PROFILE_PICTURE_GALLERY_OPTION)) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Choose from Gallery",
                        modifier = Modifier.size(Dimensions.IconSize.large))
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                    Text("Gallery", style = MaterialTheme.typography.bodyLarge)
                  }
            }
      }
}

@Composable
fun GalleryDialog(
    pageNumber: Int,
    galleryPictureUrl: List<String>?,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
          // Image or default icon
          if (!galleryPictureUrl.isNullOrEmpty() && pageNumber < galleryPictureUrl.size) {
            AsyncImage(
                model = galleryPictureUrl[pageNumber],
                contentDescription = "Discussion Profile Picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { onDismiss() })
          } else {
            Box(
                modifier = Modifier.fillMaxSize().clickable { onDismiss() },
                contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = Icons.Default.AddAPhoto,
                      contentDescription = "Default Add Photo Icon",
                      modifier =
                          Modifier.size(
                              Dimensions.IconSize.massive.times(Dimensions.Multipliers.quadruple)),
                      tint = Color.White.copy(alpha = Dimensions.Alpha.dialogIconTranslucent))
                }
          }

          PhotoDialogTopBar(
              Modifier.align(Alignment.TopCenter),
              if (!galleryPictureUrl.isNullOrEmpty() && pageNumber < galleryPictureUrl.size) {
                "Image ${pageNumber + 1} of ${galleryPictureUrl.size}"
              } else {
                "Add Photo"
              },
              onDismiss)

          PhotoDialogBottomBar(
              Modifier.align(Alignment.BottomCenter), onTakePhoto, onChooseFromGallery)
        }
      }
}

@Preview(showBackground = true)
@Composable
fun M3ImageCarouselPreview() {
  ImageCarousel(photoCollectionUrl = listOf(), modifier = Modifier.fillMaxWidth().padding(16.dp))
}

@Preview(showBackground = true)
@Composable
fun GalleryDialogPreview() {
  GalleryDialog(
      pageNumber = 0,
      galleryPictureUrl = listOf("https://via.placeholder.com/300"),
      onDismiss = {},
      onTakePhoto = {},
      onChooseFromGallery = {})
}
