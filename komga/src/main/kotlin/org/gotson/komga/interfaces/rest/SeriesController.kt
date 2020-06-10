package org.gotson.komga.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.gotson.komga.application.tasks.TaskReceiver
import org.gotson.komga.domain.model.BookSearchWithReadProgress
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.ReadStatus
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.SeriesSearchWithReadProgress
import org.gotson.komga.domain.persistence.BookRepository
import org.gotson.komga.domain.persistence.SeriesMetadataRepository
import org.gotson.komga.domain.persistence.SeriesRepository
import org.gotson.komga.domain.service.BookLifecycle
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.infrastructure.swagger.PageableAsQueryParam
import org.gotson.komga.infrastructure.swagger.PageableWithoutSortAsQueryParam
import org.gotson.komga.interfaces.rest.dto.BookDto
import org.gotson.komga.interfaces.rest.dto.SeriesDto
import org.gotson.komga.interfaces.rest.dto.SeriesMetadataUpdateDto
import org.gotson.komga.interfaces.rest.dto.restrictUrl
import org.gotson.komga.interfaces.rest.persistence.BookDtoRepository
import org.gotson.komga.interfaces.rest.persistence.SeriesDtoRepository
import org.springframework.core.io.FileSystemResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("api/v1/series", produces = [MediaType.APPLICATION_JSON_VALUE])
class SeriesController(
  private val taskReceiver: TaskReceiver,
  private val seriesRepository: SeriesRepository,
  private val seriesMetadataRepository: SeriesMetadataRepository,
  private val seriesDtoRepository: SeriesDtoRepository,
  private val bookLifecycle: BookLifecycle,
  private val bookRepository: BookRepository,
  private val bookDtoRepository: BookDtoRepository,
  private val bookController: BookController
) {

  @PageableAsQueryParam
  @GetMapping
  fun getAllSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @RequestParam(name = "search", required = false) searchTerm: String?,
    @RequestParam(name = "library_id", required = false) libraryIds: List<Long>?,
    @RequestParam(name = "status", required = false) metadataStatus: List<SeriesMetadata.Status>?,
    @RequestParam(name = "read_status", required = false) readStatus: List<ReadStatus>?,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.titleSort").ignoreCase())
    )

    val seriesSearch = SeriesSearchWithReadProgress(
      libraryIds = principal.user.getAuthorizedLibraryIds(libraryIds),
      searchTerm = searchTerm,
      metadataStatus = metadataStatus ?: emptyList(),
      readStatus = readStatus ?: emptyList()
    )

    return seriesDtoRepository.findAll(seriesSearch, principal.user.id, pageRequest)
      .map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @Operation(description = "Return recently added or updated series.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/latest")
  fun getLatestSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    )

    val libraryIds = if (principal.user.sharedAllLibraries) emptyList<Long>() else principal.user.sharedLibrariesIds

    return seriesDtoRepository.findAll(
      SeriesSearchWithReadProgress(libraryIds = libraryIds),
      principal.user.id,
      pageRequest
    ).map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @Operation(description = "Return newly added series.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/new")
  fun getNewSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "createdDate")
    )

    val libraryIds = if (principal.user.sharedAllLibraries) emptyList<Long>() else principal.user.sharedLibrariesIds

    return seriesDtoRepository.findAll(
      SeriesSearchWithReadProgress(libraryIds = libraryIds),
      principal.user.id,
      pageRequest
    ).map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @Operation(description = "Return recently updated series, but not newly added ones.")
  @PageableWithoutSortAsQueryParam
  @GetMapping("/updated")
  fun getUpdatedSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @Parameter(hidden = true) page: Pageable
  ): Page<SeriesDto> {
    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      Sort.by(Sort.Direction.DESC, "lastModifiedDate")
    )

    val libraryIds = if (principal.user.sharedAllLibraries) emptyList<Long>() else principal.user.sharedLibrariesIds

    return seriesDtoRepository.findRecentlyUpdated(
      SeriesSearchWithReadProgress(libraryIds = libraryIds),
      principal.user.id,
      pageRequest
    ).map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @GetMapping("{seriesId}")
  fun getOneSeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") id: Long
  ): SeriesDto =
    seriesDtoRepository.findByIdOrNull(id, principal.user.id)?.let {
      if (!principal.user.canAccessLibrary(it.libraryId)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
      it.restrictUrl(!principal.user.roleAdmin)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @ApiResponse(content = [Content(schema = Schema(type = "string", format = "binary"))])
  @GetMapping(value = ["{seriesId}/thumbnail"], produces = [MediaType.IMAGE_JPEG_VALUE])
  fun getSeriesThumbnail(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") seriesId: Long
  ): ResponseEntity<ByteArray> {
    seriesRepository.getLibraryId(seriesId)?.let {
      if (!principal.user.canAccessLibrary(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    return bookRepository.findFirstIdInSeries(seriesId)?.let {
      bookRepository.findByIdOrNull(it)?.let { firstBook ->
        var dirPath = firstBook.path().parent.toString()
        if (dirPath.endsWith(File.separatorChar)) {
          dirPath = dirPath.substring(0, dirPath.length - 1)
        }

        var response: ResponseEntity<ByteArray>? = null

        for (ext in COVER_EXTS) {
          val thumbFile = FileSystemResource(File("$dirPath.$ext").toPath())
          if (thumbFile.exists()) {
            val fileBytes = thumbFile.uri.toURL().readBytes()
            response = ResponseEntity.ok()
              .setCachePrivate()
              .body(fileBytes)
            break
          }
        }

        if (response == null) {
          response = bookController.getBookThumbnail(principal, it)
        }

        return response
      } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
  }

  @PageableAsQueryParam
  @GetMapping("{seriesId}/books")
  fun getAllBooksBySeries(
    @AuthenticationPrincipal principal: KomgaPrincipal,
    @PathVariable(name = "seriesId") seriesId: Long,
    @RequestParam(name = "media_status", required = false) mediaStatus: List<Media.Status>?,
    @RequestParam(name = "read_status", required = false) readStatus: List<ReadStatus>?,
    @Parameter(hidden = true) page: Pageable
  ): Page<BookDto> {
    seriesRepository.getLibraryId(seriesId)?.let {
      if (!principal.user.canAccessLibrary(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    val pageRequest = PageRequest.of(
      page.pageNumber,
      page.pageSize,
      if (page.sort.isSorted) Sort.by(page.sort.map { it.ignoreCase() }.toList())
      else Sort.by(Sort.Order.asc("metadata.numberSort"))
    )

    return bookDtoRepository.findAll(
      BookSearchWithReadProgress(
        seriesIds = listOf(seriesId),
        mediaStatus = mediaStatus ?: emptyList(),
        readStatus = readStatus ?: emptyList()
      ),
      principal.user.id,
      pageRequest
    ).map { it.restrictUrl(!principal.user.roleAdmin) }
  }

  @PostMapping("{seriesId}/analyze")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun analyze(@PathVariable seriesId: Long) {
    bookRepository.findAllIdBySeriesId(seriesId).forEach {
      taskReceiver.analyzeBook(it)
    }
  }

  @PostMapping("{seriesId}/metadata/refresh")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun refreshMetadata(@PathVariable seriesId: Long) {
    bookRepository.findAllIdBySeriesId(seriesId).forEach {
      taskReceiver.refreshBookMetadata(it)
    }
  }

  @PatchMapping("{seriesId}/metadata")
  @PreAuthorize("hasRole('ADMIN')")
  fun updateMetadata(
    @PathVariable seriesId: Long,
    @Parameter(description = "Metadata fields to update. Set a field to null to unset the metadata. You can omit fields you don't want to update.")
    @Valid @RequestBody newMetadata: SeriesMetadataUpdateDto,
    @AuthenticationPrincipal principal: KomgaPrincipal
  ): SeriesDto =
    seriesMetadataRepository.findByIdOrNull(seriesId)?.let { existing ->
      val updated = with(newMetadata) {
        existing.copy(
          status = status ?: existing.status,
          statusLock = statusLock ?: existing.statusLock,
          title = title ?: existing.title,
          titleLock = titleLock ?: existing.titleLock,
          titleSort = titleSort ?: existing.titleSort,
          titleSortLock = titleSortLock ?: existing.titleSortLock
        )
      }
      seriesMetadataRepository.update(updated)
      seriesDtoRepository.findByIdOrNull(seriesId, principal.user.id)!!
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

  @PostMapping("{seriesId}/read-progress")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun markAsRead(
    @PathVariable seriesId: Long,
    @AuthenticationPrincipal principal: KomgaPrincipal
  ) {
    seriesRepository.getLibraryId(seriesId)?.let {
      if (!principal.user.canAccessLibrary(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    bookRepository.findAllIdBySeriesId(seriesId).forEach {
      bookLifecycle.markReadProgressCompleted(it, principal.user)
    }
  }

  @DeleteMapping("{seriesId}/read-progress")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun markAsUnread(
    @PathVariable seriesId: Long,
    @AuthenticationPrincipal principal: KomgaPrincipal
  ) {
    seriesRepository.getLibraryId(seriesId)?.let {
      if (!principal.user.canAccessLibrary(it)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    bookRepository.findAllIdBySeriesId(seriesId).forEach {
      bookLifecycle.deleteReadProgress(it, principal.user)
    }
  }

  private fun ResponseEntity.BodyBuilder.setCachePrivate() =
    this.cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS)
      .cachePrivate()
      .mustRevalidate()
    )
}
