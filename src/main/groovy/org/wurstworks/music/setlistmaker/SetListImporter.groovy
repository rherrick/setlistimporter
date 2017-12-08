package org.wurstworks.music.setlistmaker

import java.nio.file.Paths

//Column 1: Title
//Column 2: Artist
//Column 3: Tags
//Column 4: Key
//Column 5: Time signature
//Column 6: Tempo
//Column 7: Duration
//Column 8: Starting pitch
//Column 9: Document filenames
//Column 10: Lyrics
//Column 11: Chords
//Column 12: Notes
//Column 13: MIDI song number
//Column 14: MIDI program changes (incoming)
//Columns 15+: Custom fields

def headers = ["Title", "Artist", "Tags", "Key", "Time signature", "Tempo", "Duration", "Starting pitch", "Document filenames", "Lyrics", "Chords", "Notes", "MIDI song number", "MIDI program changes (incoming)"]

def cli = new CliBuilder(usage: 'SetListImporter <output> <input1> ... <inputn>', header: """
Imports one or more inputs and transforms them into rows in the file importSongs.txt in the folder indicated by the
output argument. SetListImporter creates the output file if it doesn't already exist. If it does exist, the rows for the
input files will replace any rows in the existing output file. Note that the inputs can be a specific file or a folder,
in which case all files within the folder are processed. Currently, file wildcards are not supported.

If only a single folder is indicated, that will be used both as the output folder for the importSongs.txt document and
the source folder for input chord files.
""")
def options = cli.parse(args)
def arguments = options.arguments()
if (arguments.size() < 1) {
    println "You must specify at least one argument to SetListImporter: a folder containing the songs to be processed and where the output importSongs.txt should be written."
    System.exit(-1)
}

def output = arguments.size() == 1 ? arguments.get(0) : arguments.remove(0)
def folder = new File(output)
if (!folder.exists()) {
    if (!folder.mkdirs()) {
        println "Something seemed to go wrong when creating the output folder. Please check for error messages."
        System.exit(-1)
    } else {
        println "Successfully created the output folder ${folder.path}."
    }
} else if (!folder.isDirectory()) {
    println "The output location specified ${folder.path} already exists but is not a folder as required."
    System.exit(-1)
}
def file = Paths.get(output, "importSongs.txt").toFile()
if (file.exists()) {
    println "Found existing file ${file.path} and will write all transformed input files there."
} else {
    println "Creating new output file ${file.path} and will write all transformed input files there."
}

file.withWriter { writer ->
    arguments.each { argument ->
        def File input = new File(argument)
        if (!input.exists()) {
            println "The file ${argument} does not exist."
        } else if (input.isDirectory()) {
            input.eachFileRecurse {
                if (!it.name.equals("importSongs.txt")) {
                    process(headers, writer, it)
                }
            }
        } else {
            process(headers, writer, input)
        }
    }
}

def process(def headers, def Writer writer, def File input) {
    def row = [:]
    def chords = new StringBuilder()
    def lyrics = new StringBuilder()
    def notes = new StringBuilder()
    def ignore = false
    input.withReader { reader ->
        def isFirst = true
        def inChords = false
        def inLyrics = false
        def inNotes = false
        reader.readLines().each { line ->
            if (isFirst) {
                isFirst = false
                if (line.equals("IGNORE")) {
                    ignore = true
                }
            }
            if (line.startsWith("Chords:")) {
                inChords = true
                inLyrics = inNotes = false
            } else if (line.startsWith("Lyrics:")) {
                inLyrics = true
                inChords = inNotes = false
            } else if (line.startsWith("Notes:")) {
                inNotes = true
                inLyrics = inChords = false
            } else {
                if (!inNotes && !inChords && !inLyrics) {
                    def atoms = line.split(":", 2)
                    if (atoms.length == 2) {
                        row[atoms[0]] = atoms[1].trim()
                    }
                } else if (inNotes) {
                    notes.append(line.replaceAll("\t", "    ")).append("\\n")
                } else if (inChords) {
                    chords.append(line.replaceAll("\t", "    ")).append("\\n")
                } else {
                    lyrics.append(line.replaceAll("\t", "    ")).append("\\n")
                }
            }
        }
    }
    if (!ignore) {
        row["Notes"] = notes.toString() - ~/^(\\n|\\t|\s+)+/ - ~/(\\n|\\t|\s+)+$/
        row["Chords"] = chords.toString() - ~/^(\\n|\\t|\s+)+/ - ~/(\\n|\\t|\s+)+$/
        row["Lyrics"] = lyrics.toString()
        // row["Lyrics"] = lyrics.toString() - ~/^(\\n|\\t|\s+)+/ - ~/(\\n|\\t|\s+)+$/
        headers.each { def String header ->
            def String item = row[header] ?: ""
            writer << item << "\t"
        }
        writer << "\n"
    }
}
