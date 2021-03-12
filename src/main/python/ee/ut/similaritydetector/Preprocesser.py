import io
import sys
import tokenize


def preprocess_source_code(source_code):
    source_code_io = io.StringIO(source_code)
    output = ""
    prev_tok_type = tokenize.INDENT
    last_line_nr = -1
    last_col = 0
    for tok in tokenize.generate_tokens(source_code_io.readline):
        token_type = tok[0]
        token_string = tok[1]
        start_line, start_col = tok[2]
        end_line, end_col = tok[3]
        l_text = tok[4]
        if start_line > last_line_nr:
            last_col = 0
        if start_col > last_col:
            output += (" " * (start_col - last_col))
        if token_type == tokenize.COMMENT:
            pass
        elif token_type == tokenize.STRING:
            if prev_tok_type != tokenize.INDENT:
                if prev_tok_type != tokenize.NEWLINE:
                    if start_col > 0:
                        output += token_string
        else:
            output += token_string
        prev_tok_type = token_type
        last_col = end_col
        last_line_nr = end_line
    output = '\n'.join(l for l in output.splitlines() if l.strip())
    return output


# File path is passed as a command line argument
source_code_filepath = sys.argv[1]
preprocessed_code_filepath = source_code_filepath[0: len(source_code_filepath) - 3] + "_preprocessed.py"

with open(source_code_filepath, 'r', encoding="utf-8") as source_code_file:
    preprocessed_code = preprocess_source_code(source_code_file.read())
    with open(preprocessed_code_filepath, 'w', encoding="utf-8") as preprocessed_code_file:
        preprocessed_code_file.write(preprocessed_code)