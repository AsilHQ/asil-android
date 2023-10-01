// Copyright 2022 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

function sendMessage(message) {
    console.log(message);
    try {
        window.__firefox__.execute(function($) {
            let postMessage = $(function(message) {
                $.postNativeMessage('$<message_handler>', {
                    "securityToken": SECURITY_TOKEN,
                    "state": message
                });
            });

            postMessage(message);
        });
    }
    catch {}
}

function removeSourceElementsInPictures() {
    const pictureElements = document.querySelectorAll('picture');

    pictureElements.forEach(picture => {
        const sourceElements = picture.querySelectorAll('source');
        sourceElements.forEach(source => {
            source.remove();
        });
    });
}

function blurImage(image) {
  image.style.filter = 'blur(10px)';
}

function unblurImageOnLoad(image) {
  image.onload = () => {
      image.style.filter = 'none';
  };
}

//Means that there is no object in image
function unblurImage(image) {
    image.style.filter = 'none';
}

function setImageSrc(element, url) {
    const isBackgroundImage = element.getAttribute('hasBackgroundImage') && element.tagName !== "IMG" && element.tagName !== "image";
    if (isBackgroundImage) {
        element.style.backgroundImage = `url(${url})`;
        element.setAttribute('data-replaced', 'true');
        unblurImage(element);
    }
    else {
        element.src = url;
        element.removeAttribute('data-lazysrc');
        element.removeAttribute('srcset');
        element.removeAttribute('data-srcset');
        element.setAttribute('data-replaced', 'true');
        unblurImageOnLoad(element);
        if (element.dataset) {
            element.dataset.src = url;
        }
    }
    sendMessage("replaced");
}

async function replaceImagesWithApiResults(apiUrl = 'https://api.safegaze.com/api/v1/analyze') {
  const batchSize = 4;
  const minImageSize = 40; // Minimum image size in pixels

  const hasMinRenderedSize = (element) => {
    const rect = element.getBoundingClientRect();
    return rect.width >= minImageSize && rect.height >= minImageSize;
  };

  const replaceImages = async (batch) => {
    // Create the request body.
    const requestBody = {
     media: batch.map(imgElement => {
           return {
             media_url: imgElement.getAttribute('src'),
             media_type: imgElement.getAttribute('hasBackgroundImage') && imgElement.tagName !== "IMG" && imgElement.tagName !== "image" ? "backgroundImage" : "image",
             has_attachment: false,
             srcAttr: imgElement.getAttribute('srcAttr')
           };
         })
    };

    try {
      // Mark the URLs of all images in the current batch as sent in requests
      batch.forEach(imgElement => {
        imgElement.setAttribute('isSent', 'true');
      });

      // Send the request to the API.
      const response = await fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
      });

      // Check if response status is ok
      if (!response.ok) {
        sendMessage('HTTP error, status = ' + response.status);
        return;
      }
      else {
        sendMessage("Response success")
      }

      // Extract the response data from the response.
      const responseBody = await response.json();
      if (responseBody.media.length === 0) {
          sendMessage('Empty response');
      }
      else {
          if (responseBody.success) {
            batch.forEach((element, index) => {
                  const correspondingMedia = responseBody.media.find(media => element.src === media.original_media_url || element.src.includes(media.original_media_url));
                  if (correspondingMedia) {
                      if (correspondingMedia.success) {
                          setImageSrc(element, correspondingMedia.processed_media_url);
                      }
                      else {
                          unblurImage(element);
                      }
                  }
            });
          } else {
            console.error('API request failed:', responseBody.errors);
          }
      }
    } catch (error) {
      console.error('Error occurred during API request:', error);
    }
  };

  // Scroll event listener
  const fetchNewImages = async () => {
     removeSourceElementsInPictures();
     const backgroundImages = Array.from(document.querySelectorAll(':not([isSent="true"]):not([data-replaced="true"]):not([alt="logo"]):not([src*="captcha"])')).filter(img => {
           const backgroundImage = img.style.backgroundImage;
           if (backgroundImage) {
               const backgroundImageUrl = backgroundImage.slice(5, -2);
               const hasBackgroundImage = backgroundImage.startsWith("url(");
               if (hasBackgroundImage && img.tagName !== "IMG" && !backgroundImageUrl.includes('.svg')) {
                  blurImage(img);
                  img.setAttribute('hasBackgroundImage', 'true');
                  img.setAttribute('isSent', 'true');
                  img.setAttribute('src', backgroundImageUrl);
                  return true;
               }
           }
           return false;
     });
     const imageElements = Array.from(document.querySelectorAll('img[src]:not([src*="logo"]):not([src*=".svg"]):not([src*="no-image"]):not([isSent="true"]):not([data-replaced="true"]):not([alt="logo"]):not([src*="captcha"])')).filter(img => {
        const src = img.getAttribute('src');
        const alt = img.getAttribute('alt');
        const id = img.getAttribute('id');
        if (img.parentElement.classList.contains('captcha') || (id && id.includes('captcha'))) {
             return false;
        }
        if (src && !src.startsWith('data:image/') && src.length > 0) {
            if (hasMinRenderedSize(img)) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                return true;
            }
            else {
                return false;
            }
        }
        else if (!src || src.length === 0) {
            if (img.getAttribute("xlink:href")) {
                img.setAttribute('src', img.getAttribute("xlink:href"));
                img.setAttribute('srcAttr', "xlink:href");
                blurImage(img);
                img.setAttribute('isSent', 'true');
                return true;
            }
        }
        blurImage(img);
        return false;
    });

    const lazyImageElements = Array.from(document.querySelectorAll('img[data-src]:not([data-src*="logo"]):not([data-src*=".svg"]):not([data-src*="no-image"]):not([isSent="true"]):not([data-replaced="true"]):not([alt="logo"]:not([data-src*="captcha"])')).filter(img => {
        const dataSrc = img.getAttribute('data-src');
        const alt = img.getAttribute('alt');
        const id = img.getAttribute('id');
        if (img.parentElement.classList.contains('captcha') || (id && id.includes('captcha'))) {
             return false;
        }
        if (dataSrc && !dataSrc.startsWith('data:image/') && dataSrc.length > 0) {
            if (hasMinRenderedSize(img)) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                img.setAttribute('src', dataSrc);
                return true;
            }
            else {
                return false;
            }
        }
        else if (!dataSrc || dataSrc.length === 0) {
            if (img.getAttribute("xlink:href")) {
                img.setAttribute('src', img.getAttribute("xlink:href"));
                img.setAttribute('srcAttr', "xlink:href");
                blurImage(img);
                img.setAttribute('isSent', 'true');
                return true;
            }
        }
        blurImage(img);
        return false;
    });
    const allImages = [...imageElements, ...lazyImageElements, ...backgroundImages];
    if (allImages.length > 0) {
         const cleanedSavedImagesArray = []
         const analyzePromises = [];
         allImages.forEach(imgElement => {
           var mediaUrl = imgElement.getAttribute('src') || imgElement.getAttribute('data-src');
           var absoluteUrl = new URL(mediaUrl, window.location.origin).href;
           if (absoluteUrl) {
               mediaUrl = absoluteUrl;
           }

           let analyzer = new RemoteAnalyzer({ mediaUrl });
           const analyzePromise = analyzer.analyze().then((result) => {
             if (!result.shouldMask) {
               imgElement.src = mediaUrl
               cleanedSavedImagesArray.push(imgElement)
             } else {
               setImageSrc(imgElement, result.maskedUrl);
             }
           }).catch((err) => {
             sendMessage("Error analyzing media:" + err);
           });
           analyzePromises.push(analyzePromise);
         })

         await Promise.all(analyzePromises)

         const newBatches = [];

          for (let i = 0; i < cleanedSavedImagesArray.length; i += batchSize) {
            newBatches.push(cleanedSavedImagesArray.slice(i, i + batchSize));
          }

          for (const batch of newBatches) {
            // Filter out images that have already been replaced or sent in previous requests
             const imagesToReplace = batch.filter(imgElement => {
                 const srcValue = imgElement.getAttribute('src');
                 return !imgElement.hasAttribute('data-replaced') && !srcValue.startsWith('data:image/');
             });

            if (imagesToReplace.length > 0) {
              await replaceImages(imagesToReplace);
            }
          }
        }
  };
  fetchNewImages();
  window.addEventListener('scroll', fetchNewImages);
}

class RemoteAnalyzer {
  constructor(data) {
    this.data = data;
  }

  analyze = async () => {
    try {
      let relativeFilePath = this.relativeFilePath(this.data.mediaUrl);
      if (await this.urlExists(relativeFilePath)) {
        return {
          shouldMask: true,
          maskedUrl: relativeFilePath,
        };
      }
    } catch (error) {
      sendMessage(error.toString());
    }
    return {
      shouldMask: false,
      maskedUrl: ""
    };
  };

  urlExists = async (url) => {
      try {
        const response = await fetch(url, {
          method: "GET",
          cache: "no-cache"
        });
        return response.ok;
      } catch (error) {
        sendMessage(error.toString());
        return false;
      }
    };

  relativeFilePath = (originalMediaUrl) => {
    let url = decodeURIComponent(originalMediaUrl);
    let urlParts = url.split("?");

    // Handling protocol stripped URL
    let protocolStrippedUrl = urlParts[0]
      .replace(/http:\/\//, "")
      .replace(/https:\/\//, "")
      .replace(/--/g, "__")
      .replace(/%/g, "_");

    // Handling query parameters
    let queryParams =
      urlParts[1] !== undefined
        ? urlParts[1].replace(/,/g, "_").replace(/=/g, "_").replace(/&/g, "/")
        : "";

    let relativeFolder = protocolStrippedUrl.split("/").slice(0, -1).join("/");
    if (queryParams.length) {
      relativeFolder = `${relativeFolder}/${queryParams}`;
    }

    // Handling file and extension
    let filenameWithExtension = protocolStrippedUrl.split("/").pop();
    let filenameParts = filenameWithExtension.split(".");
    let filename, extension;
    if (filenameParts.length >= 2) {
      filename = filenameParts.slice(0, -1).join(".");
      extension = filenameParts.pop();
    } else {
      filename = filenameParts[0].length ? filenameParts[0] : "image";
      extension = "jpg";
    }

    return `https://cdn.safegaze.com/annotated_image/${relativeFolder}/${filename}.${extension}`;
  };
}

replaceImagesWithApiResults();
