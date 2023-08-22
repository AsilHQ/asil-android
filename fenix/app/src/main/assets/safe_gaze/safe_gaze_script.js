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
  const unblurImages = (image) => {
    const container = image.parentElement; // Get the container that holds the image and spinner
    const spinner = container.querySelector('.spinner');
    if (spinner) {
      // If image's data changed wait for load, if didn't remove spinner immediately
      if (image.hasAttribute('data-replaced')) {
        // Wait for the image to be fully loaded before removing the spinner
        image.onload = () => {
          spinner.remove();
          image.style.filter = 'none';
        };

        // If image can't be loaded, then set src to it's initial state and try to unblur again.
        image.onerror = (event) => {
          image.src = image.getAttribute('original-image-url')
          unblurImages(image)
        };
      } else {
        spinner.remove();
        image.style.filter = 'none';
      }
    }
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

      // Create a timeout promise
      const timeoutPromise = new Promise((resolve, reject) => {
        setTimeout(() => {
          reject(new Error('Request timed out'));
        }, 5000); // Adjust the timeout value in milliseconds (5 seconds in this example)
      });

      // Send the request to the API.
      const fetchPromise = fetch(apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      // Wait for either the fetch promise to resolve or the timeout promise to reject
      const response = await Promise.race([fetchPromise, timeoutPromise]);

      if (response instanceof Response) {
        // Check if response status is ok
        if (!response.ok) {
          unblurBatch(batch)
          return;
        }

        // Extract the response data from the response.
        const responseBody = await response.json();

        if (responseBody.success) {
          responseBody.media.forEach((media, index) => {
            const processedMediaUrl = media.success ? media.processed_media_url : null;
            let elementIndex = batch.findIndex(item => item.src === media.original_media_url || item.src.includes(media.original_media_url));
            let element = batch[elementIndex];
            if (processedMediaUrl !== null) {
              element.src = processedMediaUrl;
              element.srcset = '';
              element.setAttribute('data-replaced', 'true');
              element.setAttribute('from-db', 'false')
              if (element.dataset) {
                element.dataset.src = processedMediaUrl;
              }
            }
          });
        } else {
          console.error('API request failed:', responseBody.errors);
        }
      } else {
        // Handle timeout case here if needed
        console.error('Timeout occurred');
      }
      unblurBatch(batch)
    } catch (error) {
      console.error('Error occurred during API request:', error);
      unblurBatch(batch)
    }
  };

  function unblurBatch(batch) {
    batch.forEach(imgElement => {
      unblurImages(imgElement);
    });
  }

  // Scroll event listener
  const fetchNewImages = async () => {
    const imageElements = Array.from(document.getElementsByTagName('img')).filter(img => {
      const src = img.getAttribute('src');
      const alt = img.getAttribute('alt');
      if (src && !src.startsWith('data:image/')) {
        const isValidImage = !src.includes('.svg') && hasMinRenderedSize(img) && img.getAttribute('alt') !== 'logo' && !src.includes('logo') && img.getAttribute('isSent') !== 'true' && img.getAttribute('data-replaced') !== 'true' && !src.includes('no-image')
        if (isValidImage) {
          blurImage(img);
          img.setAttribute('isSent', 'true');
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
        return true;
      }
      return false;
    });

    const lazyImageElements = Array.from(document.querySelectorAll('img[data-src]')).filter(img => {
      const dataSrc = img.getAttribute('data-src');
      const alt = img.getAttribute('alt');
      if (dataSrc && !dataSrc.startsWith('data:image/')) {
        const isValidImage = !dataSrc.includes('.svg') && hasMinRenderedSize(img) && img.getAttribute('alt') !== 'logo' && !dataSrc.includes('logo') && img.getAttribute('isSent') !== 'true' && img.getAttribute('data-replaced') !== 'true' && !dataSrc.includes('no-image')
        if (isValidImage) {
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
        img.setAttribute('src', img.getAttribute("xlink:href"));
        img.setAttribute('srcAttr', "xlink:href");
        blurImage(img);
        img.setAttribute('isSent', 'true');
        return true;
      }
      return false;
    });

    const allImages = [...imageElements, ...lazyImageElements];
    if (allImages.length > 0) {
      const cleanedSavedImagesArray = []
      const analyzePromises = [];
      allImages.forEach(imgElement => {
        let mediaUrl = imgElement.getAttribute('src') || imgElement.getAttribute('data-src');
        imgElement.setAttribute('original-image-url', mediaUrl)
        if (mediaUrl.startsWith('/wp-content')) {
          const protocol = window.location.protocol; // "http:" or "https:"
          const host = window.location.host; // "www.xyz.com" or your domain
          mediaUrl = `${protocol}//${host}${mediaUrl}`; // Use mediaUrl instead of url here
        }
        else if (mediaUrl.startsWith('//')) {
          mediaUrl = 'https:' + mediaUrl;
        }

        let analyzer = new RemoteAnalyzer({ mediaUrl });
        const analyzePromise = analyzer.analyze().then((result) => {
          if (!result.shouldMask) {
            imgElement.src = mediaUrl
            cleanedSavedImagesArray.push(imgElement)
          } else {
            imgElement.src = result.maskedUrl
            imgElement.srcset = '';
            imgElement.setAttribute('data-replaced', 'true');
            imgElement.setAttribute('from-db', 'true')
            unblurImages(imgElement);
            if (imgElement.dataset) {
              imgElement.dataset.src = result.maskedUrl;
            }
          }
        }).catch((err) => { });

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
        console.log('url exist')
        return {
          shouldMask: true,
          maskedUrl: relativeFilePath,
        };
      }
    } catch (error) { }

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


