// Copyright 2022 The Brave Authors. All rights reserved.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

const css = `
.spinner {
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

// Create a style element and append it to the head to embed the CSS styles
const style = document.createElement('style');
style.innerHTML = css;
document.head.appendChild(style);


async function replaceImagesWithApiResults(apiUrl = 'https://api.safegaze.com/api/v1/analyze') {
  const batchSize = 4;
  const minImageSize = 40; // Minimum image size in pixels

  const hasMinRenderedSize = (element) => {
    const rect = element.getBoundingClientRect();
    return rect.width >= minImageSize && rect.height >= minImageSize;
  };

  const blurImage = (image) => {
       image.style.filter = 'blur(10px)';
       const spinner = document.createElement('div');
       spinner.classList.add('spinner');
       image.parentElement.appendChild(spinner);
  };

  // Function to remove blur effect and spinner from images
  const unblurImages = (images) => {
    images.forEach(imgElement => {
        const container = imgElement.parentElement; // Get the container that holds the image and spinner
        const spinner = container.querySelector('.spinner');
        if (spinner) {
          // Wait for the image to be fully loaded before removing the spinner
          imgElement.onload = () => {
            spinner.remove();
            imgElement.style.filter = 'none';
          };
        }
    });
  };

  const replaceImages = async (batch) => {
    // Create the request body.
    const requestBody = {
    media: batch.map(imgElement => {
          let mediaUrl = imgElement.getAttribute('src') || imgElement.getAttribute('data-src');
          console.log('Media url:', mediaUrl);
          if (mediaUrl.startsWith('/wp-content')) {
              const protocol = window.location.protocol; // "http:" or "https:"
              const host = window.location.host; // "www.xyz.com" or your domain
              mediaUrl = `${protocol}//${host}${mediaUrl}`; // Use mediaUrl instead of url here
              console.log('Host', host)
              console.log('Prefixed Url', mediaUrl);
          }
          else if (mediaUrl.startsWith('//')) {
            mediaUrl = 'https:' + mediaUrl;
          }
          return {
            media_url: mediaUrl,
            media_type: 'image',
            has_attachment: false,
            srcAttr: imgElement.getAttribute('srcAttr')
          };
        })
      };

    console.log('Sending request:', JSON.stringify(requestBody)); // Log request body

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
        console.error('HTTP error, status = ' + response.status);
        return;
      }
      else {
        console.log("Response ok")
      }

      // Extract the response data from the response.
      const responseBody = await response.json();

      console.log('Received response:', responseBody); // Log response body

      if (responseBody.success) {
        responseBody.media.forEach((media, index) => {
          const processedMediaUrl = media.success ? media.processed_media_url : null;

          if (processedMediaUrl !== null) {
            batch[index].src = processedMediaUrl;
            if (batch[index].dataset) {
              batch[index].dataset.src = processedMediaUrl;
              unblurImages([batch[index]]);
            }
            window.__firefox__.execute(function($) {
                let postMessage = $(function(message) {
                  $.postNativeMessage('$<message_handler>', {
                    "securityToken": SECURITY_TOKEN,
                    "state": message
                  });
                });

                postMessage("replaced");
            });

          }
          batch[index].setAttribute('data-replaced', 'true');
        });
      } else {
        console.error('API request failed:', responseBody.errors);
      }
    } catch (error) {
      console.error('Error occurred during API request:', error);
    }
  };

  // Scroll event listener
  const fetchNewImages = async () => {
     const imageElements = Array.from(document.getElementsByTagName('img')).filter(img => {
        const src = img.getAttribute('src');
        const alt = img.getAttribute('alt');
        if (src && !src.startsWith('data:image/')) {
            const isValidImage = !src.includes('.svg') && hasMinRenderedSize(img) && img.getAttribute('alt') !== 'logo' && !src.includes('logo') && img.getAttribute('isSent') !== 'true' && img.getAttribute('data-replaced') !== 'true'
            if (isValidImage) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                console.log('SRC:', src);
                return true;
            }
            else {
                return false;
            }
        }
        else if (!src || src.length === 0) {
            img.setAttribute('src', img.getAttribute("xlink:href"));
            img.setAttribute('srcAttr', "xlink:href");
            blurImage(img);
            img.setAttribute('isSent', 'true');
            console.log('xlink:', src);
            return true;
        }
        return false;
    });

    const lazyImageElements = Array.from(document.querySelectorAll('img[data-src]')).filter(img => {
        const dataSrc = img.getAttribute('data-src');
        const alt = img.getAttribute('alt');
        if (dataSrc && !dataSrc.startsWith('data:image/')) {
            const isValidImage = !dataSrc.includes('.svg') && hasMinRenderedSize(img) && img.getAttribute('alt') !== 'logo' && !dataSrc.includes('logo') && img.getAttribute('isSent') !== 'true' && img.getAttribute('data-replaced') !== 'true'
            if (isValidImage) {
                blurImage(img);
                img.setAttribute('isSent', 'true');
                console.log('SRC:', dataSrc);
                return true;
            }
            else {
                return false;
            }
        }
        else if (!dataSrc || dataSrc.length === 0) {
            img.setAttribute('src', img.getAttribute("xlink:href"));
            img.setAttribute('srcAttr', "xlink:href");
            blurImage(img);
            img.setAttribute('isSent', 'true');
            console.log('xlink:', src);
            return true;
        }
        return false;
    });
    const allImages = [...imageElements, ...lazyImageElements];

    if (allImages.length > 0) {
      const newBatches = [];
      for (let i = 0; i < allImages.length; i += batchSize) {
        newBatches.push(allImages.slice(i, i + batchSize));
      }

      for (const batch of newBatches) {
        // Filter out images that have already been replaced or sent in previous requests
        const imagesToReplace = batch.filter(imgElement => !imgElement.hasAttribute('data-replaced') && !imgElement.startsWith('data:image/'));

        if (imagesToReplace.length > 0) {
          await replaceImages(imagesToReplace);
        }
      }
    }
    else {
        console.log("EMPTY");
    }
  };
  fetchNewImages();
  window.addEventListener('scroll', fetchNewImages);
}

replaceImagesWithApiResults();
