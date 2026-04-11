import sys
import os
from bs4 import BeautifulSoup

def convert_html_to_xml(input_html_path, output_xml_path):
    """
    Reads an HTML file and writes out correctly formatted XML.
    Uses BeautifulSoup which repairs broken/unclosed tags automatically.
    """
    if not os.path.exists(input_html_path):
        print(f"Error: Could not find input file '{input_html_path}'")
        sys.exit(1)

    print(f"Reading HTML from: {input_html_path}")
    with open(input_html_path, 'r', encoding='utf-8') as f:
        html_content = f.read()

    # Parse using BeautifulSoup - 'lxml-xml' or just 'xml' enforces XML rules (closing all tags)
    # Using 'html.parser' first lets us gracefully handle messy HTML
    soup = BeautifulSoup(html_content, 'html.parser')

    print(f"Writing XML to: {output_xml_path}")
    with open(output_xml_path, 'w', encoding='utf-8') as f:
        # Prettify with the 'xml' formatter will ensure everything is well-formed XML
        # (e.g., <br> becomes <br/>)
        f.write(soup.prettify(formatter="xml"))

    print("Success! Conversion complete.")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python html_to_xml.py <input_html_file> <output_xml_file>")
        print("Example: python html_to_xml.py index.html output.xml")
        sys.exit(1)
        
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    convert_html_to_xml(input_file, output_file)
