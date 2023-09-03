// Copyright 2022 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

const customSpinnerSafegazeCSS = `
.custom-spinner-safegaze {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border: 4px solid rgba(0, 0, 0, 0.3);
  border-top: 4px solid #3498db;
  border-radius: 50%;
  width: 25px;
  margin-left: -12.5px;
  margin-top: -12.5px;
  height: 25px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}`;

const customSpinnerSafegazeStyle = document.createElement('style');
customSpinnerSafegazeStyle.innerHTML = customSpinnerSafegazeCSS;
document.head.appendChild(customSpinnerSafegazeStyle);

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
     const spinner = document.createElement('div');
     spinner.classList.add('custom-spinner-safegaze');
     image.parentElement.appendChild(spinner);
}

function onlyBlurImage(image) {
     image.style.filter = 'blur(10px)';
}

function unblurImages(image) {
  const container = image.parentElement; // Get the container that holds the image and spinner
  const spinner = container.querySelector('.custom-spinner-safegaze');
  if (spinner) {
    // Wait for the image to be fully loaded before removing the spinner
    image.onload = () => {
      spinner.remove();
      image.style.filter = 'none';
    };
  } else {
    image.onload = () => {
      image.style.filter = 'none';
    };
  }
}

function removeSpinner(image) {
    const container = image.parentElement; // Get the container that holds the image and spinner
    const spinner = container.querySelector('.custom-spinner-safegaze');
    if (spinner) {
        spinner.remove();
    }
}

function setImageSrc(element, url) {
    element.src = url;
    element.removeAttribute('data-lazysrc');
    element.removeAttribute('srcset');
    element.removeAttribute('data-srcset');
    element.setAttribute('data-replaced', 'true');
    unblurImages(element);
    if (element.dataset) {
        element.dataset.src = url;
    }
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
             media_type: 'image',
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
        batch.forEach(imgElement => {
            removeSpinner(imgElement);
        });
        return;
      }
      else {
        sendMessage("Response success")
      }

      // Extract the response data from the response.
      const responseBody = await response.json();
      if (responseBody.media.length === 0) {
          sendMessage('Empty response');
          batch.forEach(imgElement => {
              removeSpinner(imgElement);
          });
      }
      else {
          if (responseBody.success) {
            batch.forEach((element, index) => {
                  const correspondingMedia = responseBody.media.find(media => element.src === media.original_media_url || element.src.includes(media.original_media_url));
                  if (correspondingMedia) {
                      const processedMediaUrl = correspondingMedia.success ? correspondingMedia.processed_media_url : null;
                      if (processedMediaUrl !== null) {
                          setImageSrc(element, processedMediaUrl);
                          sendMessage("replaced");
                      } else {
                          sendMessage('Response true but not processed' + element.src);
                          removeSpinner(element);
                      }
                  }
                  else {
                      removeSpinner(element);
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
     const imageElements = Array.from(document.querySelectorAll('img[src]:not([src*="logo"]):not([src*=".svg"]):not([src*="no-image"]):not([isSent="true"]):not([data-replaced="true"]):not([alt="logo"])')).filter(img => {
        const src = img.getAttribute('src');
        const alt = img.getAttribute('alt');
        if (src && !src.startsWith('data:image/') && src.length > 0) {
            if (hasMinRenderedSize(img)) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                sendMessage('SRC:', src);
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
                sendMessage('xlink:', src);
                return true;
            }
        }
        onlyBlurImage(img);
        return false;
    });

    const lazyImageElements = Array.from(document.querySelectorAll('img[data-src]:not([data-src*="logo"]):not([data-src*=".svg"]):not([data-src*="no-image"]):not([isSent="true"]):not([data-replaced="true"]):not([alt="logo"])')).filter(img => {
        const dataSrc = img.getAttribute('data-src');
        const alt = img.getAttribute('alt');
        if (dataSrc && !dataSrc.startsWith('data:image/') && dataSrc.length > 0) {
            if (hasMinRenderedSize(img)) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                img.setAttribute('src', dataSrc);
                sendMessage('Data SRC:', dataSrc);
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
                sendMessage('xlink:', src);
                return true;
            }
        }
        onlyBlurImage(img);
        return false;
    });
    const allImages = [...imageElements, ...lazyImageElements];
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
             sendMessage("Media analysis complete"+ result);
           }).catch((err) => {
             sendMessage("Error analyzing media:"+ err);
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
        sendMessage("URL exists" + relativeFilePath);
        return {
          shouldMask: true,
          maskedUrl: relativeFilePath,
        };
      } else {
        sendMessage("URL does not exist" + relativeFilePath);
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