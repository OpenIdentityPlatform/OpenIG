/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */


define([], () => ({
    isClipboardEnabled () {
        // method queryCommandSupported in all browsers (since IE6+, Firefox 2+, Chrome 1+ etc)
        return document.queryCommandSupported("copy");
    },

    copyContent (element) {
        let copied = false;
        const selection = this.selectElementContent(element);
        try {
            copied = document.execCommand("copy");
        } catch (err) {
            console.log("CopyContent failed", err);
        }
        if (copied) {
            selection.removeAllRanges();
        }
        return copied;
    },

    selectElementContent (element) {
        const range = document.createRange();
        range.selectNodeContents(element);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        return sel;
    }
})
);
